package SDD.smash.domain.recommendation.infrastructure.external;


import SDD.smash.global.exception.DomainException;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiRequest;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static SDD.smash.global.exception.ErrorCode.OPENAI_SERVER_ERROR;
import static SDD.smash.global.exception.ErrorCode.OPENAI_TOKEN_EXPIRED;

@Component
@Slf4j
public class OpenAiClient {
    private final RestClient restClient;
    private final String APIKEY;
    private final URI APIURL;

    public OpenAiClient(RestClient restClient, @Value("${openai.api-key}") String apiKey,
                        @Value("${openai.api-url}") String apiUrl) {
        this.restClient = restClient;
        // 설정값을 URI 로 확정해 둔다. String 오버로드는 URI 템플릿으로 재해석된다.
        this.APIURL = URI.create(apiUrl);
        this.APIKEY = apiKey;
    }
    /**
     * 사용자 질문을 GPT 모델에 전달하고 응답 받기.
     *
     * <p>비-2xx 응답은 {@code onStatus} 에서 {@link DomainException} 으로 번역한다
     * (429 → 토큰/쿼터 소진, 5xx → 서버 오류). 그 밖의 4xx 는 {@code retrieve()} 기본
     * 핸들러가 {@code RestClientException} 으로 던지며, 타임아웃도 {@code ResourceAccessException}
     * 으로 나간다. 상위 어댑터가 이들을 모두 폴백(빈 목록/null)으로 흡수한다.
     */
    public OpenAiResponse getChatCompletion(OpenAiRequest requestDto) {
        OpenAiResponse body = restClient.post()
                .uri(APIURL)
                .contentType(MediaType.APPLICATION_JSON)
                // RestClient 는 RestTemplate 과 달리 Accept 를 자동으로 채우지 않는다.
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + APIKEY)
                .body(requestDto)
                .retrieve()
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw new DomainException(OPENAI_TOKEN_EXPIRED, "OpenAI 요청 한도를 초과했습니다.");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new DomainException(OPENAI_SERVER_ERROR, "OpenAI 서버 오류가 발생했습니다.");
                })
                .body(OpenAiResponse.class);
        if (body == null) {
            throw new DomainException(OPENAI_SERVER_ERROR, "OpenAI 응답 본문이 비어 있습니다.");
        }
        return body;
    }

}
