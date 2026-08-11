package SDD.smash.recommendation.infrastructure.external;


import SDD.smash.common.exception.DomainException;
import SDD.smash.recommendation.infrastructure.external.dto.OpenAiRequest;
import SDD.smash.recommendation.infrastructure.external.dto.OpenAiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static SDD.smash.common.exception.ErrorCode.OPENAI_TOKEN_EXPIRED;

@Component
@Slf4j
public class OpenAiClient {
    private final RestTemplate restTemplate;
    private final String APIKEY;
    private final String APIURL;

    public OpenAiClient(RestTemplate restTemplate, @Value("${openai.api-key}") String apiKey,
                        @Value("${openai.api-url}") String apiUrl) {
        this.restTemplate = restTemplate;
        this.APIURL = apiUrl;
        this.APIKEY = apiKey;
    }
    /**
     * 사용자 질문을 GPT 모델에 전달하고 응답 받기.
     * */
    public OpenAiResponse getChatCompletion(OpenAiRequest requestDto) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(APIKEY);

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(requestDto, headers);

        ResponseEntity<OpenAiResponse> res = restTemplate.postForEntity(
                APIURL, entity, OpenAiResponse.class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new DomainException(OPENAI_TOKEN_EXPIRED, "토큰이 만료되었습니다.");
        }
        return res.getBody();
    }

}
