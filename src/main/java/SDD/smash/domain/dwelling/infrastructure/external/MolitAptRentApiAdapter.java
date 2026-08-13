package SDD.smash.domain.dwelling.infrastructure.external;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.MonthlyRentResult;
import SDD.smash.domain.dwelling.domain.model.RentCollection;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * 국토부 아파트 전월세 실거래 API 어댑터. {@code RentRecordProvider} 포트 구현이다.
 *
 * <p>외부 API 어휘({@code LAWD_CD}, {@code DEAL_YMD}, {@code serviceKey}, {@code totalCount})는
 * 이 클래스 밖으로 나가지 않는다. 도메인은 "시군구와 연월로 실거래를 받는다"만 안다.
 *
 * <h2>페이지네이션</h2>
 * 이전 구현은 {@code pageNo=1, numOfRows=1000} 을 고정하고 {@code totalCount} 를 보지 않았다.
 * 강남구(11680) 202605 는 {@code totalCount=1892} 라 <b>892건이 조용히 버려졌다</b>.
 * 평균·중앙값을 내는 배치에서 표본 절반이 빠지면 결과가 왜곡되고, 그 왜곡은 어떤 검증에도 걸리지 않는다.
 * 지금은 첫 페이지의 {@code totalCount} 를 읽어 필요한 페이지를 끝까지 받는다.
 *
 * <h2>호출 제한</h2>
 * 개발계정 트래픽이 10,000건/일이고 게이트웨이에 초당 호출 제한(에러코드 23)이 있다.
 * 시군구 264개 × 12개월 × 페이지 수만큼 호출하므로 <b>최소 호출 간격</b>과 <b>동시 호출 수</b>를
 * 프로퍼티로 조인다.
 *
 * <h2>비밀값</h2>
 * URL 을 로그에 남길 때 {@code serviceKey} 를 마스킹한다. 응답 본문은 운영 로그에 찍지 않는다.
 */
@Component
@Slf4j
public class MolitAptRentApiAdapter implements RentRecordProvider {

    private static final DateTimeFormatter DEAL_YMD_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final String SERVICE_KEY_PARAM = "serviceKey";
    private static final String SERVICE_KEY_MASK = SERVICE_KEY_PARAM + "=****";

    /** 이미 URL 인코딩된 키인지 판정하는 흔적. */
    private static final String[] ENCODED_KEY_MARKERS = {"%2B", "%2F", "%3D"};

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    @Value("${apis.molit.base-url}")
    private String baseUrl;
    @Value("${apis.molit.path}")
    private String apiPath;
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

    private Semaphore concurrencyPermits;
    private final Object intervalLock = new Object();
    private long nextAllowedAtMillis;

    public MolitAptRentApiAdapter(RestTemplate restTemplate,
                                  ObjectMapper objectMapper, XmlMapper xmlMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.xmlMapper = xmlMapper;
    }

    @PostConstruct
    void initRateLimiter() {
        if (pageSize <= 0) {
            pageSize = 1000;
        }
        if (maxPages <= 0) {
            maxPages = 1;
        }
        this.concurrencyPermits = new Semaphore(Math.max(1, maxConcurrentRequests), true);
    }

    // ------------------------------------------------------------------ 포트 구현

    /**
     * 엄격 조회. 응답을 신뢰할 수 없으면 예외를 던진다.
     *
     * <p>빈 리스트로 삼키면 그 달이 "거래 0건"으로 집계에 들어간다. 배치 Step 의
     * 재시도/실패 처리가 동작하도록 반드시 예외로 알린다.
     */
    @Override
    public List<RentRecord> fetch(SigunguCode code, YearMonth yearMonth) {
        return fetchStrict(code, yearMonth).records();
    }

    /** 관대 조회. 실패를 예외 대신 {@link MonthlyRentResult} 상태로 돌려준다. */
    @Override
    public MonthlyRentResult fetchMonth(SigunguCode code, YearMonth yearMonth) {
        try {
            return fetchStrict(code, yearMonth);
        } catch (RuntimeException e) {
            log.warn("[MOLIT] 월 수집 실패 sigungu={}, ym={}, reason={}",
                    code.value(), yearMonth, summarize(e));
            return MonthlyRentResult.undetermined(yearMonth, 1, summarize(e));
        }
    }

    /**
     * 구간 수집. 한 달이 실패해도 나머지 달을 계속 확인하고 실패한 달을 결과에 담는다.
     * 배치 이름·기준월·호출 수·건수·소요 시간을 한 줄로 남긴다.
     */
    @Override
    public RentCollection collect(SigunguCode code, AggregationPeriod period) {
        long startedAt = System.currentTimeMillis();

        List<MonthlyRentResult> monthly = new ArrayList<>(period.monthCount());
        for (YearMonth yearMonth : period.months()) {
            monthly.add(fetchMonth(code, yearMonth));
        }
        RentCollection collection = RentCollection.from(code, period, monthly);

        long elapsedMs = System.currentTimeMillis() - startedAt;
        if (collection.hasFailures()) {
            log.error("[dwellingJob] 수집 부분 실패 sigungu={}, baseMonth={}, months={}, apiCalls={}, "
                            + "read={}, failedMonths={}, emptyMonths={}, elapsed={}ms, status=FAILED",
                    code.value(), period.to(), period.monthCount(), collection.apiCalls(),
                    collection.recordCount(), collection.failedMonths(),
                    collection.confirmedEmptyMonths().size(), elapsedMs);
        } else {
            log.info("[dwellingJob] 수집 완료 sigungu={}, baseMonth={}, months={}, apiCalls={}, "
                            + "read={}, emptyMonths={}, elapsed={}ms, status=OK",
                    code.value(), period.to(), period.monthCount(), collection.apiCalls(),
                    collection.recordCount(), collection.confirmedEmptyMonths().size(), elapsedMs);
        }
        return collection;
    }

    // ------------------------------------------------------------------ 수집 본체

    /**
     * {@code totalCount} 를 읽어 필요한 페이지를 전부 받는다.
     *
     * @throws MolitApiException 게이트웨이 오류, 실패 {@code resultCode}, {@code totalCount} 부재
     */
    private MonthlyRentResult fetchStrict(SigunguCode code, YearMonth yearMonth) {
        long startedAt = System.currentTimeMillis();

        List<RentRecord> collected = new ArrayList<>();
        int page = 1;
        int calls = 0;
        int totalCount = 0;
        int pageLimit = maxPages;

        while (page <= pageLimit) {
            MolitApiResponse response = call(code, yearMonth, page);
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

            List<RentRecord> pageRecords = extractRecords(response.items());
            collected.addAll(pageRecords);

            if (collected.size() >= totalCount || pageRecords.isEmpty()) {
                break;
            }
            page++;
        }

        if (collected.size() < totalCount) {
            // 신고 정보가 실시간으로 변경·해제되므로 페이지 사이에 총건수가 줄 수 있다.
            // 다만 페이지 상한에 걸린 경우는 설정 문제이므로 구분해 남긴다.
            log.warn("[MOLIT] 수집 건수 미달 sigungu={}, ym={}, totalCount={}, read={}, pages={}, maxPages={}",
                    code.value(), yearMonth, totalCount, collected.size(), calls, maxPages);
        }

        log.debug("[MOLIT] 월 수집 sigungu={}, ym={}, totalCount={}, read={}, apiCalls={}, elapsed={}ms",
                code.value(), yearMonth, totalCount, collected.size(), calls,
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

    private MolitApiResponse call(SigunguCode code, YearMonth yearMonth, int page) {
        String url = buildUrl(code, yearMonth, page);
        acquireSlot();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = parseJsonWithXmlFallback(response.getHeaders().getContentType(), response.getBody());
            return MolitApiResponse.of(root);
        } catch (RuntimeException e) {
            log.error("[MOLIT] 호출 실패 sigungu={}, ym={}, page={}, url={}",
                    code.value(), yearMonth, page, mask(url), e);
            throw e;
        } finally {
            concurrencyPermits.release();
        }
    }

    private String buildUrl(SigunguCode code, YearMonth yearMonth, int page) {
        String key = (serviceKey == null) ? "" : serviceKey.trim();

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .pathSegment(apiPath)
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

    private List<RentRecord> extractRecords(JsonNode items) {
        if (items == null || items.isMissingNode() || items.isNull()) {
            return List.of();
        }
        if (items.isArray()) {
            List<RentRecord> list = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                list.add(RentRecordJsonMapper.toRecord(item));
            }
            return list;
        }
        if (items.isObject()) {
            return List.of(RentRecordJsonMapper.toRecord(items));
        }
        // items 가 빈 문자열("")로 오는 정상 케이스. 거래 0건이다.
        return List.of();
    }

    // ------------------------------------------------------------------ 호출 제한

    /** 동시 호출 수와 최소 호출 간격을 함께 지킨다. 반드시 {@code finally} 에서 release 한다. */
    private void acquireSlot() {
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
