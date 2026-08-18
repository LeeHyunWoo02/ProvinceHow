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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
 *       필요하다 — <b>전국 수집이 사실상 불가능</b>하다. 그래서 일일 호출 예산을 프로퍼티로 두고
 *       초과하면 수집을 실패로 끝낸다(부분 스냅샷을 반영하지 않기 위해서다).</li>
 *   <li>초당 호출 제한(에러코드 23)이 있으나 수치가 공개되지 않아 최소 호출 간격을 둔다.</li>
 * </ul>
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
    private final long maxRetryAfterMs;

    /** 이 프로세스가 쓴 호출 수. 일일 예산 초과를 감지하기 위한 최소한의 계량기다. */
    private final AtomicInteger callsUsed = new AtomicInteger();

    private final Object intervalLock = new Object();
    private long nextAllowedAtMillis;

    public LocalDataApiAdapter(
            RestTemplate restTemplate,
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

    /** 이 프로세스가 지금까지 쓴 외부 호출 수. 배치 로그에 남긴다. */
    public int callsUsed() {
        return callsUsed.get();
    }

    // ------------------------------------------------------------------ HTTP

    private JsonNode call(String slug, LocalDataRegionCode regionCode, int page) {
        URI uri = buildUri(slug, regionCode, page);

        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            acquireSlot();
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
                return parse(response.getBody());
            } catch (HttpStatusCodeException e) {
                last = translate(e, slug, regionCode, page);
                long wait = retryAfterMillis(e.getResponseHeaders()).orElse(retryDelayMs);
                if (attempt < maxAttempts && isRetryable(e)) {
                    sleep(Math.min(wait, maxRetryAfterMs));
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
                    sleep(retryDelayMs);
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
        int used = callsUsed.incrementAndGet();
        if (used > dailyCallBudget) {
            throw new LocalDataApiException(String.format(
                    "[localdata] 일일 호출 예산 초과 used=%d, budget=%d — 전국 수집은 API 로 불가능하다."
                            + " 벌크 CSV 경로(infra.collect.source=BULK_CSV)를 쓰거나 대상 업종을 줄여라.",
                    used, dailyCallBudget));
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
