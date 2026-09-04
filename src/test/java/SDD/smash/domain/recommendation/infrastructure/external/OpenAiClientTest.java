package SDD.smash.domain.recommendation.infrastructure.external;

import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiMessage;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiRequest;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiResponse;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenAI 호출 어댑터 테스트. <b>실제 OpenAI 를 호출하지 않는다</b> — {@code MockRestServiceServer} 만 쓴다.
 *
 * <p>이 경로는 외부 어댑터 중 유일한 POST·요청 본문 직렬화·타입 매핑({@code toEntity(Class)}) 구간이고,
 * 상위 {@code OpenAiRegionPickApiAdapter} 가 실패를 빈 목록으로 흡수하므로 회귀가 로그로만 남는다.
 * 헤더 5종과 실패 시 나가는 예외 타입을 여기서 못 박는다.
 */
class OpenAiClientTest {

    private static final String API_KEY = "sk-test-secret-key-1234";
    private static final String API_URL = "https://api.openai.test/v1/chat/completions";

    private MockRestServiceServer server;
    private OpenAiClient client;

    @BeforeEach
    void setUp() {
        // RestClient 는 빌더에 바인딩한 뒤 build() 한 인스턴스를 넘겨야 목 서버가 붙는다.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiClient(builder.build(), API_KEY, API_URL);
    }

    @Test
    @DisplayName("POST 로 Content-Type·Accept·Authorization 을 모두 실어 보낸다")
    void sendsPostWithJsonContentTypeAcceptAndBearerToken() throws Exception {
        // given - 헤더 하나만 빠져도 운영에서 401/415 가 되지만 상위가 빈 목록으로 삼킨다
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        // when
        client.getChatCompletion(request());

        // then
        server.verify();
    }

    @Test
    @DisplayName("요청 본문에 모델·메시지·temperature 를 JSON 으로 직렬화해 담는다")
    void serializesModelMessagesAndTemperatureIntoRequestBody() throws Exception {
        // given
        server.expect(requestTo(API_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.temperature").value(0.3))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("추천해줘"))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        // when
        client.getChatCompletion(request());

        // then
        server.verify();
    }

    @Test
    @DisplayName("정상 응답을 OpenAiResponse 로 역직렬화한다")
    void deserializesSuccessResponseIntoOpenAiResponse() throws Exception {
        // given
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        // when
        OpenAiResponse response = client.getChatCompletion(request());

        // then
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().get(0).getMessage().getContent())
                .isEqualTo("{\"recommendations\":[]}");
    }

    @Test
    @DisplayName("401 응답은 onStatus 대상(429/5xx)이 아니라 HttpClientErrorException(RestClientException)으로 나간다")
    void propagatesHttpClientErrorOnUnauthorized() {
        // given - 429/5xx 만 DomainException 으로 번역하므로 401 은 기본 핸들러가 던진다.
        //         상위 어댑터가 RestClientException 을 폴백으로 흡수한다.
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Incorrect API key provided\"}}"));

        // when & then
        assertThatThrownBy(() -> client.getChatCompletion(request()))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class)
                .isNotInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("429 응답은 OPENAI_TOKEN_EXPIRED 로 DomainException 을 던진다")
    void translates429IntoTokenExpired() {
        // given
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"rate limit\"}}"));

        // when & then
        assertThatThrownBy(() -> client.getChatCompletion(request()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.OPENAI_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("5xx 응답은 OPENAI_SERVER_ERROR 로 DomainException 을 던진다")
    void translates5xxIntoServerError() {
        // given
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"boom\"}}"));

        // when & then
        assertThatThrownBy(() -> client.getChatCompletion(request()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.OPENAI_SERVER_ERROR);
    }

    @Test
    @DisplayName("2xx 인데 본문이 비면 OPENAI_SERVER_ERROR 로 DomainException 을 던진다")
    void throwsDomainExceptionWhenSuccessfulResponseHasNoBody() {
        // given - onStatus 를 통과한 2xx 지만 본문이 비는 유일한 경로다
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        // when & then
        assertThatThrownBy(() -> client.getChatCompletion(request()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.OPENAI_SERVER_ERROR);
    }

    @Test
    @DisplayName("실패 시 예외 메시지에 API 키가 실리지 않는다")
    void doesNotExposeApiKeyInFailureMessage() {
        // given
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Incorrect API key provided\"}}"));

        // when & then
        assertThatThrownBy(() -> client.getChatCompletion(request()))
                .hasMessageNotContaining(API_KEY);
    }

    private static OpenAiRequest request() {
        return new OpenAiRequest("gpt-test", List.of(new OpenAiMessage("user", "추천해줘", null)), 0.3);
    }

    /** OpenAI Chat Completions 응답에서 이 어댑터가 읽는 필드만 담은 합성 응답이다. */
    private static String successBody() {
        return """
                {
                  "id": "chatcmpl-test",
                  "choices": [
                    { "index": 0, "message": { "role": "assistant", "content": "{\\"recommendations\\":[]}" } }
                  ]
                }
                """;
    }
}
