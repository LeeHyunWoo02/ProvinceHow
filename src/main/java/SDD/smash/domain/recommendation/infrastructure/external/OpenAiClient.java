package SDD.smash.domain.recommendation.infrastructure.external;


import SDD.smash.global.exception.DomainException;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiRequest;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

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
     * */
    public OpenAiResponse getChatCompletion(OpenAiRequest requestDto) throws JsonProcessingException {
        ResponseEntity<OpenAiResponse> res = restClient.post()
                .uri(APIURL)
                .contentType(MediaType.APPLICATION_JSON)
                // RestClient 는 RestTemplate 과 달리 Accept 를 자동으로 채우지 않는다.
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + APIKEY)
                .body(requestDto)
                .retrieve()
                .toEntity(OpenAiResponse.class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new DomainException(OPENAI_TOKEN_EXPIRED, "토큰이 만료되었습니다.");
        }
        return res.getBody();
    }

}
