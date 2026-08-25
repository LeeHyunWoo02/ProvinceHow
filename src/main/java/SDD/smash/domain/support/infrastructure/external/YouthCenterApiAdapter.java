package SDD.smash.domain.support.infrastructure.external;

import SDD.smash.global.config.YouthCenterProperties;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.ExternalApiMetrics;
import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportPolicyCollection;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * 청년정책(온통청년) API 어댑터. {@code SupportPolicyProvider} 포트 구현이다.
 *
 * <h2>실패를 삼키지 않는다</h2>
 * 예전에는 실패를 빈 응답으로 바꿔 돌려줬고, 그 빈 목록이 정본 저장소에 저장되어
 * <b>일시적인 500 한 번이 그 (시군구, 태그) 의 멀쩡한 정책을 지웠다.</b> 지금은
 * {@link SupportPolicyCollection#notCollected()} 로 "수집하지 못했다"를 알리고
 * 저장 여부는 유스케이스가 판단한다(LOCALDATA 부분 반영 금지와 같은 결이다).
 *
 * <h2>재시도</h2>
 * 지연은 <b>지수 백오프</b>다 — 기본값 기준 1초 → 2초이며 {@code max-retry-after-ms} 가 상한이다.
 * 서버가 {@code Retry-After} 를 명시하면 그 값을 우선한다. 계산식과 설정 이름은
 * {@code infra} 의 {@code LocalDataApiAdapter} 와 맞췄다(컨텍스트가 달라 코드는 공유하지 않는다).
 * 재시도하는 상태는 {@code 429 / 403 / 5xx} 다 — 403 은 이 서버가 호출 과다를 429 대신
 * 403/500 으로 흩뿌리는 실측 패턴 때문에 포함했다. {@code 400/401/404} 는 다시 불러도
 * 같은 결과라 즉시 포기한다.
 *
 * <h2>호출 간격</h2>
 * 264 시군구 × 4 태그 = 1,056회를 쉬지 않고 때리던 것이 403/500 산발의 직접 원인으로 보인다.
 * {@code request-interval-ms} 로 <b>호출 시작 사이의 최소 간격</b>을 강제한다
 * ({@code LocalDataApiAdapter.acquireSlot()} 과 같은 방식이며, 간격 제어를 어댑터에 둔 이유는
 * 외부 서버가 견디는 속도를 아는 쪽이 어댑터이기 때문이다).
 *
 * <h2>비밀값</h2>
 * URL 을 문자열로 조립하지 않고 {@code uriBuilder.queryParam(...)} 에 맡긴다(한글 태그 인코딩).
 * <b>어떤 로그에도 조립된 URL 을 남기지 않는다</b> — {@code apiKeyNm} 이 쿼리에 들어가기 때문이다.
 * WebClient 의 예외 메시지는 쿼리를 제거한 URI 만 담으므로 예외를 그대로 로깅해도 안전하다.
 *
 * <p>외부 API 어휘({@code zipCd}, {@code plcyKywdNm}, {@code apiKeyNm})는 이 클래스 밖으로 나가지 않는다.
 */
@Component
@Slf4j
public class YouthCenterApiAdapter implements SupportPolicyProvider {

    static final String PARAM_API_KEY = "apiKeyNm";
    static final String PARAM_PAGE_NUM = "pageNum";
    static final String PARAM_PAGE_SIZE = "pageSize";
    static final String PARAM_RETURN_TYPE = "rtnType";
    static final String PARAM_ZIP_CODE = "zipCd";
    static final String PARAM_KEYWORD = "plcyKywdNm";

    private static final int PAGE_NUM = 1;
    private static final int PAGE_SIZE = 100;
    private static final String RETURN_TYPE = "json";

    private final WebClient webClient;
    private final YouthCenterProperties properties;

    /** 호출 성공/실패 계측. 수집 실패는 기존 데이터를 보존하며 조용히 지나가므로 지표가 필요하다. */
    private final ExternalApiMetrics externalApiMetrics;

    /** 메트릭의 api 태그 값. 수집원 단위다. */
    private static final String API_NAME = "youthcenter";

    private final Duration requestTimeout;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final double retryBackoffMultiplier;
    private final long maxRetryAfterMs;
    private final long requestIntervalMs;

    private final Object intervalLock = new Object();
    private long nextAllowedAtMillis;

    public YouthCenterApiAdapter(
            WebClient webClient,
            YouthCenterProperties properties,
            @Value("${apis.youthcenter.request-timeout-ms:10000}") long requestTimeoutMs,
            @Value("${apis.youthcenter.max-attempts:3}") int maxAttempts,
            @Value("${apis.youthcenter.retry-delay-ms:1000}") long retryDelayMs,
            @Value("${apis.youthcenter.retry-backoff-multiplier:2}") double retryBackoffMultiplier,
            @Value("${apis.youthcenter.max-retry-after-ms:30000}") long maxRetryAfterMs,
            @Value("${apis.youthcenter.request-interval-ms:1000}") long requestIntervalMs,
            ExternalApiMetrics externalApiMetrics) {

        this.webClient = webClient;
        this.properties = properties;
        this.externalApiMetrics = externalApiMetrics;
        this.requestTimeout = Duration.ofMillis(Math.max(1_000L, requestTimeoutMs));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
        // 1 미만이면 지연이 줄어들어 백오프가 아니게 된다. 설정 실수를 여기서 흡수한다.
        this.retryBackoffMultiplier = Math.max(1.0d, retryBackoffMultiplier);
        this.maxRetryAfterMs = Math.max(0, maxRetryAfterMs);
        this.requestIntervalMs = Math.max(0, requestIntervalMs);
    }

    // ------------------------------------------------------------------ 포트 구현

    @Override
    public SupportPolicyCollection fetch(SigunguCode code, SupportTag tag) {
        if (apiKey().isEmpty()) {
            // 빈 키로 1,056회를 때려봐야 403 만 받는다. 호출 자체를 하지 않고 수집 실패로 알린다.
            log.warn("[YouthCenter] API 키가 비어 있다(apis.youthcenter.api-key / YOUTH_API_KEY)."
                    + " 호출하지 않는다 sigungu={}, tag={}", code.value(), tag);
            externalApiMetrics.skipped(API_NAME);
            return SupportPolicyCollection.notCollected();
        }

        for (int i = 1; i <= maxAttempts; i++) {
            final int attempt = i;
            if (!acquireSlot()) {
                return SupportPolicyCollection.notCollected();
            }
            try {
                return toCollection(request(code, tag), code, tag);

            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (attempt < maxAttempts && isRetryable(status)) {
                    long wait = retryAfterMillis(e.getHeaders())
                            .map(value -> Math.min(value, maxRetryAfterMs))
                            .orElseGet(() -> backoffDelayMs(attempt));
                    log.debug("[YouthCenter] 재시도 sigungu={}, tag={}, status={}, attempt={}/{}, wait={}ms",
                            code.value(), tag, status, attempt, maxAttempts, wait);
                    if (!sleep(wait)) {
                        return SupportPolicyCollection.notCollected();
                    }
                    continue;
                }
                log.warn("[YouthCenter] fetch 실패 sigungu={}, tag={}, status={}, attempts={}"
                        + " — 저장을 건너뛰고 기존 데이터를 보존한다", code.value(), tag, status, attempt, e);
                return SupportPolicyCollection.notCollected();

            } catch (RuntimeException e) {
                // 타임아웃·연결 실패가 여기로 온다(block() 이 감싼 예외 포함).
                if (attempt < maxAttempts) {
                    long wait = backoffDelayMs(attempt);
                    log.debug("[YouthCenter] 재시도 sigungu={}, tag={}, attempt={}/{}, wait={}ms, cause={}",
                            code.value(), tag, attempt, maxAttempts, wait, e.getClass().getSimpleName());
                    if (!sleep(wait)) {
                        return SupportPolicyCollection.notCollected();
                    }
                    continue;
                }
                log.warn("[YouthCenter] fetch 실패 sigungu={}, tag={}, attempts={}"
                        + " — 저장을 건너뛰고 기존 데이터를 보존한다", code.value(), tag, attempt, e);
                return SupportPolicyCollection.notCollected();
            }
        }
        return SupportPolicyCollection.notCollected();
    }

    // ------------------------------------------------------------------ HTTP

    /**
     * 쿼리 파라미터를 문자열로 붙이지 않는다. 태그는 한글({@code 주거지원} 등)이라
     * 문자열 연결로는 인코딩되지 않은 URL 이 나가 400 의 원인이 된다.
     */
    private YouthCenterApiResponse request(SigunguCode code, SupportTag tag) {
        try {
            YouthCenterApiResponse response = doRequest(code, tag);
            externalApiMetrics.success(API_NAME);
            return response;
        } catch (RuntimeException e) {
            externalApiMetrics.failure(API_NAME);
            throw e;
        }
    }

    private YouthCenterApiResponse doRequest(SigunguCode code, SupportTag tag) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path())
                        .queryParam(PARAM_API_KEY, apiKey())
                        .queryParam(PARAM_PAGE_NUM, PAGE_NUM)
                        .queryParam(PARAM_PAGE_SIZE, PAGE_SIZE)
                        .queryParam(PARAM_RETURN_TYPE, RETURN_TYPE)
                        .queryParam(PARAM_ZIP_CODE, code.value())
                        .queryParam(PARAM_KEYWORD, tag.getValue())
                        .build())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(YouthCenterApiResponse.class)
                .timeout(requestTimeout)
                .block();
    }

    /**
     * 응답을 수집 결과로 옮긴다.
     *
     * <p>{@code result} 나 {@code youthPolicyList} 자체가 없는 응답은 <b>0건이 아니라 수집 실패</b>로
     * 본다(오류 봉투일 가능성이 크다). 정말 0건인 응답은 빈 배열로 온다. 이 구분이 없으면
     * 오류 봉투가 "정책 0건"으로 저장되어 기존 데이터를 지운다.
     */
    private SupportPolicyCollection toCollection(YouthCenterApiResponse response, SigunguCode code, SupportTag tag) {
        if (response == null || response.getResult() == null
                || response.getResult().getYouthPolicyList() == null) {
            log.warn("[YouthCenter] 응답에 정책 목록이 없다 sigungu={}, tag={}, resultCode={}"
                            + " — 저장을 건너뛰고 기존 데이터를 보존한다",
                    code.value(), tag, response == null ? null : response.getResultCode());
            return SupportPolicyCollection.notCollected();
        }
        return SupportPolicyCollection.of(response.getResult().getYouthPolicyList().stream()
                .map(p -> new SupportPolicy(p.getPlcyNm(), p.getAplyUrlAddr(), p.getPlcyKywdNm()))
                .toList());
    }

    // ------------------------------------------------------------------ 호출 제한

    /**
     * 최소 호출 간격을 지킨다. 인터럽트되면 {@code false} 를 돌려주고 호출을 포기한다.
     *
     * <p>간격은 <b>호출 시작 시각</b> 사이로 잰다({@code LocalDataApiAdapter.acquireSlot()} 과 동일).
     */
    private boolean acquireSlot() {
        if (requestIntervalMs <= 0) {
            return true;
        }
        long waitMs;
        synchronized (intervalLock) {
            long now = System.currentTimeMillis();
            waitMs = Math.max(0, nextAllowedAtMillis - now);
            nextAllowedAtMillis = Math.max(now, nextAllowedAtMillis) + requestIntervalMs;
        }
        return sleep(waitMs);
    }

    /** {@code attempt}(1부터)번째 시도가 실패한 뒤 기다릴 시간. */
    long backoffDelayMs(int attempt) {
        return backoffDelayMs(retryDelayMs, retryBackoffMultiplier, attempt, maxRetryAfterMs);
    }

    /**
     * 지수 백오프 지연. {@code base * multiplier^(attempt-1)} 를 {@code maxDelayMs} 로 자른다.
     *
     * <p>실제로 {@code sleep} 하지 않는 순수 계산이라 테스트가 느려지지 않는다.
     * 기본값(base 1000ms, 배수 2, 상한 30000ms)이면 1000 → 2000 → 4000 이다.
     * {@code LocalDataApiAdapter} 의 같은 이름 메서드와 계산식이 동일하다.
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
            // HTTP-date 형식도 규격상 가능하나 이 서버의 실측 사례가 없다. 기본 지연으로 돌린다.
            return Optional.empty();
        }
    }

    /**
     * 재시도할 상태인가.
     *
     * <p>{@code 403} 은 인증 실패가 아니라 호출 과다일 때도 이 서버가 돌려준다(고정된 시군구·태그가
     * 아니라 매번 다른 조합에서 흩어지는 실측 패턴). 키가 정말 잘못됐다면 전 조합이 실패하며
     * 로그로 드러난다. {@code 400/401/404} 는 다시 불러도 같은 결과라 재시도하지 않는다.
     */
    private static boolean isRetryable(int status) {
        return status == 429 || status == 403 || status >= 500;
    }

    /** @return 인터럽트되면 {@code false}. 호출자는 즉시 포기한다. */
    private static boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ------------------------------------------------------------------ 보조

    private String path() {
        String value = properties.getPath();
        return value == null ? "" : value.trim();
    }

    private String apiKey() {
        String value = properties.getApiKey();
        return value == null ? "" : value.trim();
    }
}
