package SDD.smash.domain.infra.infrastructure.external;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.port.InfraFacilityProvider;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

/**
 * 공식 인허가 데이터 API 어댑터. {@code InfraFacilityProvider} 포트의 <b>기본</b> 구현이다.
 *
 * <h2>왜 신 API 인가</h2>
 * 구 LOCALDATA({@code localdata.go.kr})는 2026-04-16 폐쇄됐고 {@code authKey}/{@code opnSvcId}/
 * {@code trdStateGbn} 체계 전체가 폐기됐다. 데이터는 data.go.kr 업종별 API 로 이관됐고,
 * <b>업종이 파라미터가 아니라 URL 경로 slug 로 승격</b>됐다.
 *
 * <pre>
 * GET {base}/1741000/{slug}/info
 *     ?serviceKey=...&amp;pageNo=1&amp;numOfRows=100&amp;returnType=json
 *     &amp;cond%5BOPN_ATMY_GRP_CD%3A%3AEQ%5D=3000000
 *     &amp;cond%5BSALS_STTS_CD%3A%3AEQ%5D=01
 * </pre>
 *
 * <h2>제약</h2>
 * <ul>
 *   <li>{@code numOfRows} 상한은 <b>100</b>이다(Swagger 명시).</li>
 *   <li>개발계정 트래픽이 <b>10,000회/일</b>이다. 일반음식점 전국만 2,129,830건이라 21,299회가
 *       필요하다 — <b>하루에 전국을 다 받을 수 없다</b>. 그래서 일일 호출 예산을 프로퍼티로 두고
 *       소진되면 {@link LocalDataCallBudgetExceededException} 으로 <b>오늘은 여기까지</b>를 알린다.
 *       수집 Step 은 이것을 실패가 아니라 정상 종료로 받아 staging 에 진척을 남긴다.</li>
 *   <li>초당 호출 제한(에러코드 23)이 있으나 수치가 공개되지 않아 최소 호출 간격을 둔다.</li>
 * </ul>
 *
 * <h2>타임아웃과 재시도</h2>
 * HTTP 클라이언트는 {@link LocalDataRestTemplateConfig} 의 <b>전용</b> {@code RestTemplate} 이다
 * (공유 빈보다 읽기 타임아웃이 길다). 재시도 지연은 <b>지수 백오프</b>다 — 기본값 기준
 * 1초 → 2초 → 4초이며 {@code apis.localdata.max-retry-after-ms} 를 상한으로 쓴다. 서버가
 * {@code Retry-After} 를 명시하면 그 값을 우선한다(서버 추정이 우리 추정보다 정확하다).
 *
 * <h2>비밀값</h2>
 * URL 을 로그에 남길 때 {@code serviceKey} 를 마스킹하고, 응답 본문은 운영 로그에 찍지 않는다.
 */
@Component
@Slf4j
public class LocalDataApiAdapter implements InfraFacilityProvider {

    /** 인허가 데이터 계열의 공공데이터포털 기관 코드. 경로 고정값이다. */
    static final String AGENCY_PATH = "1741000";
    static final String OPERATION_INFO = "info";

    static final String PARAM_SERVICE_KEY = "serviceKey";
    static final String PARAM_ORG_CODE = "cond[OPN_ATMY_GRP_CD::EQ]";
    static final String PARAM_STATUS = "cond[SALS_STTS_CD::EQ]";

    private static final String SERVICE_KEY_MASK = PARAM_SERVICE_KEY + "=****";
    private static final String[] ENCODED_KEY_MARKERS = {"%2B", "%2F", "%3D"};

    /** 예산 리셋 기준 시간대. 공공데이터포털의 일일 트래픽이 한국 시간 자정에 리셋된다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** Swagger 가 명시한 한 페이지 상한. 이보다 큰 값을 요청하지 않는다. */
    static final int MAX_ROWS_PER_PAGE = 100;

    /**
     * 성공으로 인정하는 {@code resultCode}. 자릿수가 데이터셋마다 다르게 온다.
     * 실 응답은 {@code resultCode=0, resultMsg=정상} 이고 Swagger 예시는 {@code 00}/{@code 000} 이다.
     * 셋 중 하나가 아니면 실패로 본다(빈 값은 헤더가 없는 응답이라 판정하지 않는다).
     */
    private static final Set<String> SUCCESS_RESULT_CODES = Set.of("0", "00", "000");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final InfraMasterCatalog masterCatalog;

    private final String baseUrl;
    private final String serviceKey;
    private final int numOfRows;
    private final int maxPagesPerTarget;
    private final long requestIntervalMs;
    private final int dailyCallBudget;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final double retryBackoffMultiplier;
    private final long maxRetryAfterMs;

    /**
     * 호출 예산 계량기. <b>날짜(Asia/Seoul)가 바뀌면 리셋된다.</b>
     *
     * <p>서버는 며칠씩 떠 있고 수집은 하루치씩 이어달린다. 프로세스 시작 이후 누계로 세면
     * 첫날 예산을 다 쓴 순간 다음 날 이후의 수집이 영영 막힌다.
     */
    private final Object budgetLock = new Object();
    private LocalDate budgetDate;
    private int callsUsedToday;

    private final Object intervalLock = new Object();
    private long nextAllowedAtMillis;

    public LocalDataApiAdapter(
            @Qualifier(LocalDataRestTemplateConfig.LOCALDATA_REST_TEMPLATE) RestTemplate restTemplate,
            ObjectMapper objectMapper,
            InfraMasterCatalog masterCatalog,
            @Value("${apis.localdata.base-url:https://apis.data.go.kr}") String baseUrl,
            @Value("${apis.datagokr.service-key:}") String serviceKey,
            @Value("${apis.localdata.page-size:100}") int numOfRows,
            @Value("${apis.localdata.max-pages:500}") int maxPagesPerTarget,
            @Value("${apis.localdata.request-interval-ms:120}") long requestIntervalMs,
            @Value("${apis.localdata.daily-call-budget:9000}") int dailyCallBudget,
            @Value("${apis.localdata.max-attempts:3}") int maxAttempts,
            @Value("${apis.localdata.retry-delay-ms:1000}") long retryDelayMs,
            @Value("${apis.localdata.retry-backoff-multiplier:2}") double retryBackoffMultiplier,
            @Value("${apis.localdata.max-retry-after-ms:60000}") long maxRetryAfterMs) {

        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.masterCatalog = masterCatalog;
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.numOfRows = Math.min(Math.max(1, numOfRows), MAX_ROWS_PER_PAGE);
        this.maxPagesPerTarget = Math.max(1, maxPagesPerTarget);
        this.requestIntervalMs = Math.max(0, requestIntervalMs);
        this.dailyCallBudget = Math.max(1, dailyCallBudget);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
        // 1 미만이면 지연이 줄어들어 백오프가 아니게 된다. 설정 실수를 여기서 흡수한다.
        this.retryBackoffMultiplier = Math.max(1.0d, retryBackoffMultiplier);
        this.maxRetryAfterMs = Math.max(0, maxRetryAfterMs);
    }

    // ------------------------------------------------------------------ 포트 구현

    @Override
    public boolean isReady() {
        return !serviceKey.isEmpty();
    }

    @Override
    public String readinessDescription() {
        return isReady()
                ? "data.go.kr 인증키 설정됨"
                : "data.go.kr 인증키가 비어 있다(apis.datagokr.service-key / DATA_GO_KR_SERVICE_KEY)."
                        + " 빈 키로 호출하지 않는다.";
    }

    @Override
    public FacilityCollection collect(IndustryCode industryCode, LocalDataRegionCode regionCode) {
        if (!isReady()) {
            // 빈 키로 호출하면 403 만 잔뜩 받고 트래픽만 소모한다.
            throw new LocalDataApiException("[localdata] " + readinessDescription());
        }
        String slug = slugOf(industryCode);

        List<InfraFacility> collected = new ArrayList<>();
        int calls = 0;
        int page = 1;
        int totalCount = -1;

        while (page <= maxPagesPerTarget) {
            JsonNode root = call(slug, regionCode, page);
            calls++;

            JsonNode body = root.path("response").path("body");
            if (page == 1) {
                totalCount = body.path("totalCount").asInt(-1);
                if (totalCount == 0) {
                    break;
                }
            }

            List<InfraFacility> pageItems = extract(body.path("items"), regionCode);
            collected.addAll(pageItems);

            if (pageItems.isEmpty()) {
                break;
            }
            if (totalCount >= 0 && collected.size() >= totalCount) {
                break;
            }
            page++;
        }

        if (totalCount > 0 && collected.size() < totalCount) {
            // 부분 수집을 성공으로 둘 수 없다. 그대로 두면 "시설이 줄었다"는 잘못된 스냅샷이 된다.
            throw new LocalDataApiException(String.format(
                    "[localdata] 수집 미완료 industry=%s, org=%s, totalCount=%d, read=%d, pages=%d, maxPages=%d",
                    industryCode.value(), regionCode.value(), totalCount, collected.size(), calls, maxPagesPerTarget));
        }

        FacilityCollection collection = FacilityCollection.of(collected, calls);
        log.debug("[localdata] 수집 industry={}, org={}, totalCount={}, read={}, dedup={}, operating={}, apiCalls={}",
                industryCode.value(), regionCode.value(), totalCount, collection.readCount(),
                collection.duplicatesDropped(), collection.operatingCount(), calls);
        return collection;
    }

    /**
     * 오늘 남은 호출 예산이 있는가.
     *
     * <p>{@code false} 는 실패가 아니라 "오늘은 여기까지"다. 수집 Step 은 이 신호를 보고
     * 스트림을 끝내 <b>COMPLETED</b> 로 마치고, 남은 대상은 다음 실행이 이어받는다.
     */
    @Override
    public boolean hasRemainingCapacity() {
        synchronized (budgetLock) {
            // 날짜가 바뀌면 아직 리셋 전이라도 예산이 살아 있는 것으로 본다(리셋은 다음 호출에서).
            return !today().equals(budgetDate) || callsUsedToday < dailyCallBudget;
        }
    }

    /** 오늘 쓴 외부 호출 수. 배치 로그에 남긴다. */
    public int callsUsed() {
        synchronized (budgetLock) {
            return today().equals(budgetDate) ? callsUsedToday : 0;
        }
    }

    /** 하루 호출 예산. 로그에 남긴다. */
    public int dailyCallBudget() {
        return dailyCallBudget;
    }

    // ------------------------------------------------------------------ HTTP

    private JsonNode call(String slug, LocalDataRegionCode regionCode, int page) {
        URI uri = buildUri(slug, regionCode, page);

        RuntimeException last = null;
        for (int i = 1; i <= maxAttempts; i++) {
            final int attempt = i;
            acquireSlot();
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
                return parse(response.getBody());
            } catch (HttpStatusCodeException e) {
                last = translate(e, slug, regionCode, page);
                // 서버가 대기시간을 명시하면 그 값이 우리 추정보다 정확하다. 없을 때만 백오프를 쓴다.
                long wait = retryAfterMillis(e.getResponseHeaders())
                        .map(value -> Math.min(value, maxRetryAfterMs))
                        .orElseGet(() -> backoffDelayMs(attempt));
                if (attempt < maxAttempts && isRetryable(e)) {
                    sleep(wait);
                    continue;
                }
                throw last;
            } catch (LocalDataApiException e) {
                // 응답 자체가 오류인 경우(게이트웨이 오류, 실패 resultCode, 파싱 실패)는
                // 다시 호출해도 결과가 같다. 사유를 감싸지 말고 그대로 올린다.
                throw e;
            } catch (RuntimeException e) {
                last = new LocalDataApiException(String.format(
                        "[localdata] 호출 실패 slug=%s, org=%s, page=%d, url=%s",
                        slug, regionCode.value(), page, mask(uri.toString())), e);
                if (attempt < maxAttempts) {
                    // 읽기 타임아웃이 여기로 온다. 서버가 느려진 상태에서 같은 간격으로 다시 때리면
                    // 세 번 모두 같은 결과를 본다(2026-08 infraStep 장애). 지연을 늘려 가며 기다린다.
                    sleep(backoffDelayMs(attempt));
                    continue;
                }
                throw last;
            }
        }
        throw last == null ? new LocalDataApiException("[localdata] 호출 실패") : last;
    }

    URI buildUri(String slug, LocalDataRegionCode regionCode, int page) {
        StringBuilder url = new StringBuilder(trimTrailingSlash(baseUrl))
                .append('/').append(AGENCY_PATH)
                .append('/').append(slug)
                .append('/').append(OPERATION_INFO)
                .append('?').append(PARAM_SERVICE_KEY).append('=').append(encodedServiceKey())
                .append("&pageNo=").append(page)
                .append("&numOfRows=").append(numOfRows)
                .append("&returnType=json")
                .append('&').append(encode(PARAM_ORG_CODE)).append('=').append(encode(regionCode.value()))
                // 영업/정상만 받는다. 서버에서 걸러야 페이지 수(=호출 수)가 줄어든다.
                .append('&').append(encode(PARAM_STATUS)).append('=')
                .append(encode(BusinessStatus.OPERATING.code()));
        return URI.create(url.toString());
    }

    private String encodedServiceKey() {
        // 이미 인코딩된 키를 다시 인코딩하면 %2B 가 %252B 가 되어 인증이 깨진다.
        return looksEncoded(serviceKey) ? serviceKey : encode(serviceKey);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            throw new LocalDataApiException("[localdata] 응답 본문이 비어 있다");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            // 본문을 로그에 찍지 않는다. 인증키가 에코되는 응답이 있다.
            throw new LocalDataApiException("[localdata] 응답 파싱 실패: " + e.getClass().getSimpleName(), e);
        }

        JsonNode gateway = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (!gateway.isMissingNode()) {
            throw new LocalDataApiException("[localdata] 게이트웨이 오류 errMsg="
                    + gateway.path("errMsg").asText("") + ", reasonCode="
                    + gateway.path("returnReasonCode").asText(""));
        }

        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asText("");
        if (!resultCode.isEmpty() && !SUCCESS_RESULT_CODES.contains(resultCode)) {
            throw new LocalDataApiException("[localdata] 실패 응답 resultCode=" + resultCode
                    + ", resultMsg=" + header.path("resultMsg").asText(""));
        }
        return root;
    }

    private List<InfraFacility> extract(JsonNode items, LocalDataRegionCode regionCode) {
        JsonNode array = items.isArray() ? items : items.path("item");
        if (array.isMissingNode() || array.isNull()) {
            return List.of();
        }
        List<InfraFacility> facilities = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                InfraFacility facility = LocalDataFacilityJsonMapper.toFacility(item, regionCode);
                if (facility != null) {
                    facilities.add(facility);
                }
            }
        } else if (array.isObject()) {
            InfraFacility facility = LocalDataFacilityJsonMapper.toFacility(array, regionCode);
            if (facility != null) {
                facilities.add(facility);
            }
        }
        return facilities;
    }

    // ------------------------------------------------------------------ 호출 제한

    /** 최소 호출 간격과 일일 예산을 함께 지킨다. */
    private void acquireSlot() {
        if (!reserveCall(today())) {
            throw new LocalDataCallBudgetExceededException(String.format(
                    "[localdata] 일일 호출 예산 소진 used=%d, budget=%d — 오늘 몫은 여기까지다."
                            + " 남은 대상은 다음 실행이 이어받는다(staging 체크포인트).",
                    callsUsed(), dailyCallBudget));
        }
        if (requestIntervalMs <= 0) {
            return;
        }
        long waitMs;
        synchronized (intervalLock) {
            long now = System.currentTimeMillis();
            waitMs = Math.max(0, nextAllowedAtMillis - now);
            nextAllowedAtMillis = Math.max(now, nextAllowedAtMillis) + requestIntervalMs;
        }
        sleep(waitMs);
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

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalDataApiException("[localdata] 호출 대기 중 인터럽트", e);
        }
    }

    /** {@code attempt}(1부터)번째 시도가 실패한 뒤 기다릴 시간. 설정값을 묶어 계산에 넘긴다. */
    long backoffDelayMs(int attempt) {
        return backoffDelayMs(retryDelayMs, retryBackoffMultiplier, attempt, maxRetryAfterMs);
    }

    /**
     * 지수 백오프 지연. {@code base * multiplier^(attempt-1)} 를 {@code maxDelayMs} 로 자른다.
     *
     * <p>실제로 {@code sleep} 하지 않는 순수 계산이라 테스트가 느려지지 않는다.
     * 기본값(base 1000ms, 배수 2, 상한 60000ms)이면 1000 → 2000 → 4000 이다.
     */
    static long backoffDelayMs(long baseDelayMs, double multiplier, int attempt, long maxDelayMs) {
        if (baseDelayMs <= 0) {
            return 0;
        }
        int exponent = Math.max(0, attempt - 1);
        double delay = baseDelayMs * Math.pow(Math.max(1.0d, multiplier), exponent);
        if (Double.isNaN(delay) || delay >= maxDelayMs) {
            return Math.max(0, maxDelayMs);
        }
        return Math.min(Math.max(0, maxDelayMs), (long) delay);
    }

    static Optional<Long> retryAfterMillis(HttpHeaders headers) {
        if (headers == null) {
            return Optional.empty();
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException e) {
            // HTTP-date 형식도 규격상 가능하나 이 게이트웨이 실측 사례가 없다. 기본 지연으로 돌린다.
            return Optional.empty();
        }
    }

    private boolean isRetryable(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private LocalDataApiException translate(HttpStatusCodeException e, String slug,
                                            LocalDataRegionCode regionCode, int page) {
        int status = e.getStatusCode().value();
        String hint = switch (status) {
            case 403 -> " — 이 업종 데이터셋에 활용신청이 되어 있지 않을 수 있다(업종별 자동승인 신청 필요).";
            case 429 -> " — 호출 허용량 초과.";
            default -> "";
        };
        return new LocalDataApiException(String.format(
                "[localdata] HTTP %d slug=%s, org=%s, page=%d%s", status, slug, regionCode.value(), page, hint));
    }

    // ------------------------------------------------------------------ 보조

    private String slugOf(IndustryCode industryCode) {
        return masterCatalog.industryMaster().byCode(industryCode)
                .map(IndustryMasterEntry::slug)
                .filter(slug -> !slug.isBlank())
                .orElseThrow(() -> new LocalDataApiException(
                        "[localdata] 업종 마스터에 엔드포인트 slug 가 없다. industryCode=" + industryCode.value()));
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean looksEncoded(String key) {
        for (String marker : ENCODED_KEY_MARKERS) {
            if (key.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    static String mask(String url) {
        int start = url.indexOf(PARAM_SERVICE_KEY + "=");
        if (start < 0) {
            return url;
        }
        int end = url.indexOf('&', start);
        return end < 0
                ? url.substring(0, start) + SERVICE_KEY_MASK
                : url.substring(0, start) + SERVICE_KEY_MASK + url.substring(end);
    }
}
