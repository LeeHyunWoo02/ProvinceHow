package SDD.smash.domain.support.infrastructure.external;

import SDD.smash.domain.support.domain.model.SupportPolicyCollection;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.global.config.YouthCenterProperties;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 외부 API 를 호출하지 않는다. {@code ExchangeFunction} 으로 응답을 합성한다
 * (새 테스트 의존성 없이 {@code spring-boot-starter-test} 안에서 해결된다).
 */
class YouthCenterApiAdapterTest {

    private static final String BASE_URL = "http://localhost";
    private static final String PATH = "/youth-test/getPlcy";
    private static final String API_KEY = "test-api-key";
    private static final SigunguCode JONGNO = SigunguCode.of("11110");

    private static final String TWO_POLICIES = """
            {"resultCode":200,"resultMessage":"성공","result":{"youthPolicyList":[
              {"plcyNm":"청년월세지원","aplyUrlAddr":"https://example.test/a","plcyKywdNm":"주거지원"},
              {"plcyNm":"청년전세보증","aplyUrlAddr":"https://example.test/b","plcyKywdNm":"주거지원"}
            ]}}
            """;
    private static final String NO_POLICY = """
            {"resultCode":200,"resultMessage":"성공","result":{"youthPolicyList":[]}}
            """;
    private static final String ERROR_ENVELOPE = """
            {"resultCode":500,"resultMessage":"서버 오류"}
            """;

    private final List<URI> requestedUris = new ArrayList<>();
    private final Deque<Supplier<ClientResponse>> responses = new ArrayDeque<>();
    private WebClient webClient;
    private YouthCenterProperties properties;

    @BeforeEach
    void setUp() {
        ExchangeFunction exchange = request -> {
            requestedUris.add(request.url());
            Supplier<ClientResponse> next = responses.poll();
            if (next == null) {
                return Mono.error(new AssertionError("예상보다 많이 호출됐다"));
            }
            return Mono.just(next.get());
        };
        webClient = WebClient.builder().baseUrl(BASE_URL).exchangeFunction(exchange).build();

        properties = new YouthCenterProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setPath(PATH);
        properties.setApiKey(API_KEY);
    }

    /** 간격·지연을 0 으로 둬 테스트가 느려지지 않게 한다. 지연 계산 자체는 순수 함수로 따로 검증한다. */
    private YouthCenterApiAdapter adapter(int maxAttempts) {
        return adapter(maxAttempts, 0L);
    }

    private YouthCenterApiAdapter adapter(int maxAttempts, long requestIntervalMs) {
        return new YouthCenterApiAdapter(webClient, properties,
                5_000L, maxAttempts, 0L, 2.0d, 30_000L, requestIntervalMs);
    }

    private void enqueue(HttpStatus status, String body) {
        responses.add(() -> ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private void enqueueStatus(HttpStatus status) {
        responses.add(() -> ClientResponse.create(status).build());
    }

    // ------------------------------------------------------------------ URL 조립

    @Test
    @DisplayName("한글 태그가 퍼센트 인코딩된 쿼리로 나간다")
    void encodesKoreanTagInQueryString() {
        // given
        enqueue(HttpStatus.OK, TWO_POLICIES);

        // when
        adapter(1).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        URI uri = requestedUris.get(0);
        String encodedTag = URLEncoder.encode(SupportTag.HOUSING_SUPPORT.getValue(), StandardCharsets.UTF_8);
        assertThat(uri.getPath()).isEqualTo(PATH);
        assertThat(uri.getRawQuery()).contains("plcyKywdNm=" + encodedTag);
        assertThat(uri.getRawQuery()).doesNotContain(SupportTag.HOUSING_SUPPORT.getValue());
        assertThat(uri.getQuery())
                .contains("plcyKywdNm=" + SupportTag.HOUSING_SUPPORT.getValue())
                .contains("zipCd=" + JONGNO.value())
                .contains("apiKeyNm=" + API_KEY)
                .contains("rtnType=json");
    }

    // ------------------------------------------------------------------ 실패 / 0건

    @Test
    @DisplayName("수집에 실패하면 빈 목록이 아니라 '수집하지 못함'을 돌려준다")
    void reportsNotCollectedWhenServerFails() {
        // given
        enqueueStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        // when
        SupportPolicyCollection collection = adapter(1).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isFalse();
        assertThat(collection.policies()).isEmpty();
    }

    @Test
    @DisplayName("정책 목록이 빈 배열이면 정말 0건을 수집한 것이다")
    void reportsCollectedWhenPolicyListIsEmpty() {
        // given
        enqueue(HttpStatus.OK, NO_POLICY);

        // when
        SupportPolicyCollection collection = adapter(1).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isTrue();
        assertThat(collection.size()).isZero();
    }

    @Test
    @DisplayName("정책 목록 자체가 없는 오류 봉투는 0건이 아니라 수집 실패다")
    void reportsNotCollectedWhenPolicyListIsAbsent() {
        // given
        enqueue(HttpStatus.OK, ERROR_ENVELOPE);

        // when
        SupportPolicyCollection collection = adapter(1).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isFalse();
    }

    @Test
    @DisplayName("수집에 성공하면 외부 어휘를 도메인 정책으로 옮긴다")
    void mapsResponseToDomainPolicies() {
        // given
        enqueue(HttpStatus.OK, TWO_POLICIES);

        // when
        SupportPolicyCollection collection = adapter(1).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isTrue();
        assertThat(collection.policies()).hasSize(2);
        assertThat(collection.policies().get(0).name()).isEqualTo("청년월세지원");
        assertThat(collection.policies().get(0).applyUrl()).isEqualTo("https://example.test/a");
        assertThat(collection.policies().get(0).keyword()).isEqualTo("주거지원");
    }

    @Test
    @DisplayName("API 키가 비어 있으면 호출하지 않고 수집 실패로 알린다")
    void doesNotCallWhenApiKeyIsBlank() {
        // given
        properties.setApiKey("  ");

        // when
        SupportPolicyCollection collection = adapter(3).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isFalse();
        assertThat(requestedUris).isEmpty();
    }

    // ------------------------------------------------------------------ 재시도

    @Test
    @DisplayName("500 은 재시도하고 마지막 시도가 성공하면 수집에 성공한다")
    void retriesServerErrorAndSucceedsOnLastAttempt() {
        // given
        enqueueStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        enqueueStatus(HttpStatus.SERVICE_UNAVAILABLE);
        enqueue(HttpStatus.OK, TWO_POLICIES);

        // when
        SupportPolicyCollection collection = adapter(3).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isTrue();
        assertThat(requestedUris).hasSize(3);
    }

    @Test
    @DisplayName("403 은 호출 과다 신호로 보고 재시도한다")
    void retriesForbidden() {
        // given
        enqueueStatus(HttpStatus.FORBIDDEN);
        enqueue(HttpStatus.OK, NO_POLICY);

        // when
        SupportPolicyCollection collection = adapter(3).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isTrue();
        assertThat(requestedUris).hasSize(2);
    }

    @Test
    @DisplayName("400 은 다시 불러도 같은 결과라 재시도하지 않는다")
    void doesNotRetryBadRequest() {
        // given
        enqueueStatus(HttpStatus.BAD_REQUEST);

        // when
        SupportPolicyCollection collection = adapter(3).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isFalse();
        assertThat(requestedUris).hasSize(1);
    }

    @Test
    @DisplayName("404 도 재시도하지 않는다")
    void doesNotRetryNotFound() {
        // given
        enqueueStatus(HttpStatus.NOT_FOUND);

        // when
        adapter(3).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(requestedUris).hasSize(1);
    }

    @Test
    @DisplayName("재시도를 다 써도 실패하면 시도 횟수만큼만 호출하고 포기한다")
    void givesUpAfterMaxAttempts() {
        // given
        enqueueStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        enqueueStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        enqueueStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        // when
        SupportPolicyCollection collection = adapter(3).fetch(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(collection.collected()).isFalse();
        assertThat(requestedUris).hasSize(3);
    }

    // ------------------------------------------------------------------ 지연 계산

    @Test
    @DisplayName("재시도 지연이 지수적으로 늘어나고 상한에서 멈춘다")
    void growsRetryDelayExponentiallyUpToCap() {
        assertThat(YouthCenterApiAdapter.backoffDelayMs(1_000L, 2.0d, 1, 30_000L)).isEqualTo(1_000L);
        assertThat(YouthCenterApiAdapter.backoffDelayMs(1_000L, 2.0d, 2, 30_000L)).isEqualTo(2_000L);
        assertThat(YouthCenterApiAdapter.backoffDelayMs(1_000L, 2.0d, 3, 30_000L)).isEqualTo(4_000L);
        assertThat(YouthCenterApiAdapter.backoffDelayMs(1_000L, 2.0d, 10, 30_000L)).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("배수가 1 미만이어도 지연이 줄어들지 않는다")
    void neverShrinksRetryDelay() {
        assertThat(YouthCenterApiAdapter.backoffDelayMs(1_000L, 0.5d, 3, 30_000L)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("서버가 준 Retry-After 초를 밀리초로 읽는다")
    void readsRetryAfterHeaderAsSeconds() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "3");

        // then
        assertThat(YouthCenterApiAdapter.retryAfterMillis(headers)).contains(3_000L);
        assertThat(YouthCenterApiAdapter.retryAfterMillis(new HttpHeaders())).isEqualTo(Optional.empty());
    }

    // ------------------------------------------------------------------ 호출 간격

    @Test
    @DisplayName("연속 호출 사이에 최소 간격을 둔다")
    void keepsMinimumIntervalBetweenCalls() {
        // given
        long intervalMs = 200L;
        enqueue(HttpStatus.OK, NO_POLICY);
        enqueue(HttpStatus.OK, NO_POLICY);
        YouthCenterApiAdapter adapter = adapter(1, intervalMs);

        // when
        long startedAt = System.currentTimeMillis();
        adapter.fetch(JONGNO, SupportTag.HOUSING_SUPPORT);
        adapter.fetch(JONGNO, SupportTag.INTERN);
        long elapsed = System.currentTimeMillis() - startedAt;

        // then — 첫 호출은 즉시, 두 번째 호출이 간격만큼 기다린다
        assertThat(elapsed).isGreaterThanOrEqualTo(intervalMs);
        assertThat(requestedUris).hasSize(2);
    }
}
