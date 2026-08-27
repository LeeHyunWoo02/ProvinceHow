package SDD.smash.domain.dwelling.infrastructure.external;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.MonthlyRentResult;
import SDD.smash.domain.dwelling.domain.model.RentCollection;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.global.metrics.CallBudgetMetrics;
import SDD.smash.global.metrics.ExternalApiMetrics;
import SDD.smash.domain.dwelling.domain.port.RentRecordProvider;
import SDD.smash.global.domain.model.SigunguCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micrometer.common.lang.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * 국토부 전월세 실거래 API 어댑터. 아파트·연립다세대·단독다가구 3종을 다룬다.
 * {@code RentRecordProvider} 포트 구현이다.
 *
 * <p>외부 API 어휘({@code LAWD_CD}, {@code DEAL_YMD}, {@code serviceKey}, {@code totalCount})는
 * 이 클래스 밖으로 나가지 않는다. 도메인은 "주택유형·시군구·연월로 실거래를 받는다"만 안다.
 *
 * <p>주택유형 3종을 한 어댑터가 맡는다. 유형별로 쪼개면 각자 세마포어를 갖게 되어
 * 일일 트래픽 한도 통제가 무너지기 때문이다.
 *
 * <h2>페이지네이션</h2>
 * 이전 구현은 {@code pageNo=1, numOfRows=1000} 을 고정하고 {@code totalCount} 를 보지 않았다.
 * 강남구(11680) 202605 는 {@code totalCount=1892} 라 <b>892건이 조용히 버려졌다</b>.
 * 평균·중앙값을 내는 배치에서 표본 절반이 빠지면 결과가 왜곡되고, 그 왜곡은 어떤 검증에도 걸리지 않는다.
 * 지금은 첫 페이지의 {@code totalCount} 를 읽어 필요한 페이지를 끝까지 받는다.
 *
 * <h2>호출 제한</h2>
 * 개발계정 트래픽이 10,000건/일이고 게이트웨이에 초당 호출 제한(에러코드 23)이 있다.
 * 시군구 264개 × 12개월 × 3유형 × 페이지 수만큼 호출하므로 <b>최소 호출 간격</b>, <b>동시 호출 수</b>,
 * <b>일일 호출 예산</b>을 프로퍼티로 조인다.
 *
 * <p>일일 예산은 <b>주택유형과 무관하게 하나로</b> 센다. 한도가 3종 데이터셋에 걸쳐 공유되는지
 * 각각인지 미확인이라(docs/external-api-spec.md 4.7) 최악(공유)을 가정한 것이다.
 * 예산을 다 쓰면 {@link MolitCallBudgetExceededException} 을 던지고, 그 달은 관대 경로에서
 * <b>판정 불가(UNDETERMINED)</b> 가 된다 — 확정 0건이 아니다.
 *
 * <h2>비밀값</h2>
 * URL 을 로그에 남길 때 {@code serviceKey} 를 마스킹한다. 응답 본문은 운영 로그에 찍지 않는다.
 */
@Component
@Slf4j
public class MolitRentApiAdapter implements RentRecordProvider {

    private static final DateTimeFormatter DEAL_YMD_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final String SERVICE_KEY_PARAM = "serviceKey";
    private static final String SERVICE_KEY_MASK = SERVICE_KEY_PARAM + "=****";

    /** 이미 URL 인코딩된 키인지 판정하는 흔적. */
    private static final String[] ENCODED_KEY_MARKERS = {"%2B", "%2F", "%3D"};

    /**
     * 국토부 실거래 서비스명의 공통 접두어. base-url 에 이게 들어 있으면 서비스명까지 포함된
     * 옛 설정값이라 유형별 경로 분기가 성립하지 않는다.
     */
    private static final String SERVICE_NAME_MARKER = "RTMSDataSvc";

    /** 예산 리셋 기준 시간대. 공공데이터포털의 일일 트래픽이 한국 시간 자정에 리셋된다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    @Value("${apis.molit.base-url}")
    private String baseUrl;

    /** 유형별 엔드포인트. 서비스명이 유형마다 달라 경로를 3개로 나눠 받는다(docs/external-api-spec.md 4.1). */
    @Value("${apis.molit.paths.apartment:}")
    private String apartmentPath;

    @Value("${apis.molit.paths.multiplex-house:}")
    private String multiplexHousePath;

    @Value("${apis.molit.paths.detached-house:}")
    private String detachedHousePath;

    @Value("${apis.molit.service-key}")
    private String serviceKey;

    /** 한 페이지에 요청할 건수. 5000 까지 동작이 확인됐고 명시적 상한은 미확인이라 보수적으로 1000 이다. */
    @Value("${apis.molit.page-size:1000}")
    private int pageSize;

    /** 한 달치에 허용하는 최대 페이지 수. 응답이 이상해 페이지가 끝나지 않는 상황의 안전핀이다. */
    @Value("${apis.molit.max-pages:50}")
    private int maxPages;

    /** 연속한 두 호출 사이의 최소 간격(ms). 0 이면 제한하지 않는다. */
    @Value("${apis.molit.request-interval-ms:100}")
    private long requestIntervalMs;

    /** 동시에 진행할 수 있는 호출 수. */
    @Value("${apis.molit.max-concurrent-requests:1}")
    private int maxConcurrentRequests;

    /** 하루에 허용할 호출 수. 개발계정 한도 10,000 에 여유를 둔 값이다. 3종이 이 하나를 공유한다. */
    @Value("${apis.molit.daily-call-budget:9000}")
    private int dailyCallBudget;

    private Semaphore concurrencyPermits;
    private final Object intervalLock = new Object();
    private long nextAllowedAtMillis;

    /**
     * 호출 예산 계량기. <b>날짜(Asia/Seoul)가 바뀌면 리셋된다.</b>
     * 서버는 며칠씩 떠 있으므로 프로세스 시작 이후 누계로 세면 첫날 이후가 영영 막힌다.
     */
    private final Object budgetLock = new Object();
    private LocalDate budgetDate;
    private int callsUsedToday;

    /** 호출 성공/실패 계측. 페이지 호출 1회마다 1건 센다. */
    private final ExternalApiMetrics externalApiMetrics;

    /** 메트릭의 api 태그 값. */
    private static final String API_NAME = "molit";

    public MolitRentApiAdapter(RestTemplate restTemplate,
                               ObjectMapper objectMapper, XmlMapper xmlMapper,
                               ExternalApiMetrics externalApiMetrics,
                               CallBudgetMetrics callBudgetMetrics) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.xmlMapper = xmlMapper;
        this.externalApiMetrics = externalApiMetrics;

        // 게이지는 값을 들고 있지 않고 스크랩 때마다 이 메서드들을 호출한다(설정값 주입 이후 시점이다).
        // callsUsed() 는 날짜가 바뀌면 0 을 돌려주므로 그래프에서 일일 리셋이 그대로 보인다.
        callBudgetMetrics.register(API_NAME, this::callsUsed, this::dailyCallBudget);
    }

    @PostConstruct
    void initRateLimiter() {
        if (pageSize <= 0) {
            pageSize = 1000;
        }
        if (maxPages <= 0) {
            maxPages = 1;
        }
        if (dailyCallBudget <= 0) {
            dailyCallBudget = 1;
        }
        this.concurrencyPermits = new Semaphore(Math.max(1, maxConcurrentRequests), true);

        String normalized = stripServiceName(baseUrl);
        if (normalized != null && !normalized.equals(baseUrl)) {
            log.warn("[MOLIT] base-url 에 서비스명이 포함돼 있어 떼어냈다. MOLIT_BASE_URL 을 '{}' 로 바꿔라", normalized);
        }
        this.baseUrl = normalized;
    }

    /**
     * 옛 설정은 base-url 에 서비스명({@code .../1613000/RTMSDataSvcAptRent})까지 넣었다.
     * 그대로 두면 유형별 경로가 세그먼트 중복이 되므로 떼어내고 경고만 남긴다 — 운영 환경변수 수정 전에도 3종이 정상 동작한다.
     */
    static String stripServiceName(String rawBaseUrl) {
        if (rawBaseUrl == null) {
            return null;
        }
        String trimmed = rawBaseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash < 0 || !trimmed.substring(lastSlash + 1).startsWith(SERVICE_NAME_MARKER)) {
            return trimmed;
        }
        return trimmed.substring(0, lastSlash);
    }

    // ------------------------------------------------------------------ 포트 구현

    /**
     * 엄격 조회. 응답을 신뢰할 수 없으면 예외를 던진다.
     *
     * <p>빈 리스트로 삼키면 그 달이 "거래 0건"으로 집계에 들어간다. 배치 Step 의
     * 재시도/실패 처리가 동작하도록 반드시 예외로 알린다.
     */
    @Override
    public List<RentRecord> fetch(HousingType housingType, SigunguCode code, YearMonth yearMonth) {
        return fetchStrict(housingType, code, yearMonth).records();
    }

    /** 관대 조회. 실패를 예외 대신 {@link MonthlyRentResult} 상태로 돌려준다. */
    @Override
    public MonthlyRentResult fetchMonth(HousingType housingType, SigunguCode code, YearMonth yearMonth) {
        try {
            return fetchStrict(housingType, code, yearMonth);
        } catch (RuntimeException e) {
            log.warn("[MOLIT] 월 수집 실패 housingType={}, sigungu={}, ym={}, reason={}",
                    housingType, code.value(), yearMonth, summarize(e));
            return MonthlyRentResult.undetermined(yearMonth, 1, summarize(e));
        }
    }

    /**
     * 구간 수집. 한 달이 실패해도 나머지 달을 계속 확인하고 실패한 달을 결과에 담는다.
     * 배치 이름·기준월·호출 수·건수·소요 시간을 한 줄로 남긴다.
     */
    @Override
    public RentCollection collect(HousingType housingType, SigunguCode code, AggregationPeriod period) {
        long startedAt = System.currentTimeMillis();

        if (!hasRemainingCapacity()) {
            // 이미 소진된 뒤에도 달마다 호출을 시도하면 같은 실패 로그가 개월 수만큼 쌓인다.
            // (시군구, 유형)당 한 줄로 줄이되 결과는 동일하게 판정 불가다.
            log.warn("[MOLIT] 일일 호출 예산 소진 - 구간 전체를 판정 불가로 남긴다 housingType={}, sigungu={}, "
                            + "used={}, budget={}",
                    housingType, code.value(), callsUsed(), dailyCallBudget);
            return RentCollection.from(code, period, budgetExhaustedMonths(period));
        }

        List<MonthlyRentResult> monthly = new ArrayList<>(period.monthCount());
        for (YearMonth yearMonth : period.months()) {
            monthly.add(fetchMonth(housingType, code, yearMonth));
        }
        RentCollection collection = RentCollection.from(code, period, monthly);

        long elapsedMs = System.currentTimeMillis() - startedAt;
        if (collection.hasFailures()) {
            log.error("[dwellingJob] 수집 부분 실패 housingType={}, sigungu={}, baseMonth={}, months={}, apiCalls={}, "
                            + "read={}, failedMonths={}, emptyMonths={}, elapsed={}ms, status=FAILED",
                    housingType, code.value(), period.to(), period.monthCount(), collection.apiCalls(),
                    collection.recordCount(), collection.failedMonths(),
                    collection.confirmedEmptyMonths().size(), elapsedMs);
        } else {
            log.info("[dwellingJob] 수집 완료 housingType={}, sigungu={}, baseMonth={}, months={}, apiCalls={}, "
                            + "read={}, emptyMonths={}, elapsed={}ms, status=OK",
                    housingType, code.value(), period.to(), period.monthCount(), collection.apiCalls(),
                    collection.recordCount(), collection.confirmedEmptyMonths().size(), elapsedMs);
        }
        return collection;
    }

    /** 예산 소진으로 아예 시도하지 못한 달들. 호출 0회, 상태는 판정 불가다. */
    private List<MonthlyRentResult> budgetExhaustedMonths(AggregationPeriod period) {
        String reason = MolitCallBudgetExceededException.class.getSimpleName()
                + ": 일일 호출 예산 소진 used=" + callsUsed() + ", budget=" + dailyCallBudget;
        List<MonthlyRentResult> results = new ArrayList<>(period.monthCount());
        for (YearMonth yearMonth : period.months()) {
            results.add(MonthlyRentResult.undetermined(yearMonth, 0, reason));
        }
        return results;
    }

    // ------------------------------------------------------------------ 수집 본체

    /**
     * {@code totalCount} 를 읽어 필요한 페이지를 전부 받는다.
     *
     * @throws MolitApiException 게이트웨이 오류, 실패 {@code resultCode}, {@code totalCount} 부재
     */
    private MonthlyRentResult fetchStrict(HousingType housingType, SigunguCode code, YearMonth yearMonth) {
        long startedAt = System.currentTimeMillis();

        List<RentRecord> collected = new ArrayList<>();
        int page = 1;
        int calls = 0;
        int totalCount = 0;
        int pageLimit = maxPages;

        while (page <= pageLimit) {
            MolitApiResponse response = call(housingType, code, yearMonth, page);
            calls++;

            String gatewayError = response.gatewayError();
            if (gatewayError != null) {
                throw new MolitApiException("게이트웨이 오류: " + gatewayError);
            }
            if (!response.isSuccess()) {
                throw new MolitApiException("실패 응답 resultCode=" + response.resultCode()
                        + ", resultMsg=" + response.resultMsg());
            }

            Integer reported = response.totalCount();
            if (reported == null) {
                // totalCount 가 없으면 "0건"인지 "본문을 못 받았는지" 구분할 수 없다.
                throw new MolitApiException("totalCount 를 읽을 수 없어 0건 여부를 판정할 수 없다");
            }

            if (page == 1) {
                totalCount = reported;
                pageLimit = pageLimitFor(totalCount, code, yearMonth);
                if (totalCount == 0) {
                    return MonthlyRentResult.confirmedEmpty(yearMonth, calls);
                }
            }

            List<RentRecord> pageRecords = extractRecords(housingType, response.items());
            collected.addAll(pageRecords);

            if (collected.size() >= totalCount || pageRecords.isEmpty()) {
                break;
            }
            page++;
        }

        if (collected.size() < totalCount) {
            // 신고 정보가 실시간으로 변경·해제되므로 페이지 사이에 총건수가 줄 수 있다.
            // 다만 페이지 상한에 걸린 경우는 설정 문제이므로 구분해 남긴다.
            log.warn("[MOLIT] 수집 건수 미달 housingType={}, sigungu={}, ym={}, totalCount={}, read={}, pages={}, maxPages={}",
                    housingType, code.value(), yearMonth, totalCount, collected.size(), calls, maxPages);
        }

        log.debug("[MOLIT] 월 수집 housingType={}, sigungu={}, ym={}, totalCount={}, read={}, apiCalls={}, elapsed={}ms",
                housingType, code.value(), yearMonth, totalCount, collected.size(), calls,
                System.currentTimeMillis() - startedAt);

        return MonthlyRentResult.available(yearMonth, collected, totalCount, calls);
    }

    private int pageLimitFor(int totalCount, SigunguCode code, YearMonth yearMonth) {
        int needed = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
        if (needed > maxPages) {
            log.warn("[MOLIT] 페이지 상한 초과 sigungu={}, ym={}, totalCount={}, needed={}, maxPages={}",
                    code.value(), yearMonth, totalCount, needed, maxPages);
            return maxPages;
        }
        return needed;
    }

    // ------------------------------------------------------------------ HTTP

    private MolitApiResponse call(HousingType housingType, SigunguCode code, YearMonth yearMonth, int page) {
        String url = buildUrl(housingType, code, yearMonth, page);
        acquireSlot();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = parseJsonWithXmlFallback(response.getHeaders().getContentType(), response.getBody());
            MolitApiResponse parsed = MolitApiResponse.of(root);
            externalApiMetrics.success(API_NAME);
            return parsed;
        } catch (RuntimeException e) {
            externalApiMetrics.failure(API_NAME);
            log.error("[MOLIT] 호출 실패 housingType={}, sigungu={}, ym={}, page={}, url={}",
                    housingType, code.value(), yearMonth, page, mask(url), e);
            throw e;
        } finally {
            concurrencyPermits.release();
        }
    }

    private String buildUrl(HousingType housingType, SigunguCode code, YearMonth yearMonth, int page) {
        String key = (serviceKey == null) ? "" : serviceKey.trim();

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                // path 는 세그먼트가 2개라 pathSegment() 가 '/' 에서 예외를 던진다. 그대로 이어붙인다.
                .path(pathFor(housingType))
                .queryParam("LAWD_CD", code.value())
                .queryParam("DEAL_YMD", yearMonth.format(DEAL_YMD_FORMAT))
                .queryParam("pageNo", page)
                .queryParam("numOfRows", pageSize)
                .queryParam("_type", "json");

        if (looksEncoded(key)) {
            // 이미 인코딩된 키를 다시 인코딩하면 %2B 가 %252B 가 되어 인증이 깨진다.
            String template = builder.queryParam(SERVICE_KEY_PARAM, "{sk}").build(false).toUriString();
            return template.replace("{sk}", key);
        }
        return builder.queryParam(SERVICE_KEY_PARAM, key).build(true).toUriString();
    }

    /** 설정이 비어 있으면 아파트로 폴백하지 않고 실패시킨다. 조용한 폴백은 다른 유형의 자료를 섞어버린다. */
    private String pathFor(HousingType housingType) {
        String path = switch (housingType) {
            case APARTMENT -> apartmentPath;
            case MULTIPLEX_HOUSE -> multiplexHousePath;
            case DETACHED_HOUSE -> detachedHousePath;
        };
        if (path == null || path.isBlank()) {
            throw new MolitApiException("경로 설정이 비어 있다 housingType=" + housingType);
        }
        return path.trim();
    }

    private JsonNode parseJsonWithXmlFallback(@Nullable MediaType contentType, String body) {
        if (body == null || body.isBlank()) {
            throw new MolitApiException("응답 본문이 비어 있다");
        }
        try {
            if ((contentType != null && MediaType.APPLICATION_JSON.includes(contentType)) || looksLikeJson(body)) {
                return objectMapper.readTree(body);
            }
            return xmlMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 본문을 로그에 찍지 않는다. 인증키가 에코되는 응답이 있을 수 있다.
            throw new MolitApiException("응답 파싱 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    private List<RentRecord> extractRecords(HousingType housingType, JsonNode items) {
        if (items == null || items.isMissingNode() || items.isNull()) {
            return List.of();
        }
        if (items.isArray()) {
            List<RentRecord> list = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                list.add(RentRecordJsonMapper.toRecord(housingType, item));
            }
            return list;
        }
        if (items.isObject()) {
            return List.of(RentRecordJsonMapper.toRecord(housingType, items));
        }
        // items 가 빈 문자열("")로 오는 정상 케이스. 거래 0건이다.
        return List.of();
    }

    // ------------------------------------------------------------------ 호출 제한

    /**
     * 오늘 남은 호출 예산이 있는가. {@code false} 는 실패가 아니라 "오늘은 여기까지"다.
     */
    public boolean hasRemainingCapacity() {
        synchronized (budgetLock) {
            // 날짜가 바뀌면 아직 리셋 전이라도 예산이 살아 있는 것으로 본다(리셋은 다음 호출에서).
            return !today().equals(budgetDate) || callsUsedToday < dailyCallBudget;
        }
    }

    /** 오늘 쓴 외부 호출 수. 주택유형과 무관하게 하나로 센다. */
    public int callsUsed() {
        synchronized (budgetLock) {
            return today().equals(budgetDate) ? callsUsedToday : 0;
        }
    }

    /** 하루 호출 예산. */
    public int dailyCallBudget() {
        return dailyCallBudget;
    }

    /**
     * 오늘 예산에서 한 칸을 확보한다. 날짜가 바뀌었으면 먼저 리셋한다.
     *
     * @return 확보하지 못했으면(=예산 소진) {@code false}
     */
    boolean reserveCall(LocalDate today) {
        synchronized (budgetLock) {
            if (!today.equals(budgetDate)) {
                budgetDate = today;
                callsUsedToday = 0;
            }
            if (callsUsedToday >= dailyCallBudget) {
                return false;
            }
            callsUsedToday++;
            return true;
        }
    }

    private static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    /**
     * 일일 예산, 동시 호출 수, 최소 호출 간격을 함께 지킨다. 반드시 {@code finally} 에서 release 한다.
     *
     * <p>예산 확인은 세마포어 획득 <b>전</b>이다. 뒤에서 던지면 {@code call()} 의
     * {@code try/finally} 밖이라 permit 이 반납되지 않는다.
     */
    private void acquireSlot() {
        if (!reserveCall(today())) {
            throw new MolitCallBudgetExceededException(String.format(
                    "일일 호출 예산 소진 used=%d, budget=%d — 오늘 몫은 여기까지다."
                            + " 이 달은 판정 불가로 남고 해당 (시군구, 유형)은 집계에서 제외된다.",
                    callsUsed(), dailyCallBudget));
        }
        Semaphore permits = concurrencyPermits;
        if (permits == null) {
            initRateLimiter();
            permits = concurrencyPermits;
        }
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MolitApiException("호출 대기 중 인터럽트", e);
        }
        waitForInterval();
    }

    private void waitForInterval() {
        if (requestIntervalMs <= 0) {
            return;
        }
        long waitMs;
        synchronized (intervalLock) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextAllowedAtMillis);
            nextAllowedAtMillis = slot + requestIntervalMs;
            waitMs = slot - now;
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------------------------------------------------------ 유틸

    /** 로그에 남길 URL 에서 인증키를 지운다. */
    static String mask(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll(SERVICE_KEY_PARAM + "=[^&]*", SERVICE_KEY_MASK);
    }

    /** 예외를 한 줄 사유로 줄인다. 스택과 본문을 남기지 않는다. */
    private static String summarize(Throwable e) {
        return e.getClass().getSimpleName() + ": " + mask(String.valueOf(e.getMessage()));
    }

    private static boolean looksLikeJson(String s) {
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private static boolean looksEncoded(String key) {
        for (String marker : ENCODED_KEY_MARKERS) {
            if (key.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
