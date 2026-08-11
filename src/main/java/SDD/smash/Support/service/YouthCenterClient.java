package SDD.smash.Support.service;

import SDD.smash.common.config.YouthCenterProperties;
import SDD.smash.Support.domain.SupportTag;
import SDD.smash.Support.dto.SupportDTO;
import SDD.smash.Support.dto.SupportListDTO;
import SDD.smash.Support.dto.YouthCenterResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class YouthCenterClient {
    private final WebClient webClient;
    private final YouthCenterProperties properties;


    public FetchResult fetch(String sigunguCode, SupportTag tag)
    {
        String url = properties.getPath()
                + "?apiKeyNm=" + properties.getApiKey()
                + "&pageNum=1"
                + "&pageSize=100"
                + "&rtnType=json"
                + "&zipCd=" + sigunguCode
                + "&plcyKywdNm=" + tag.getValue();

        YouthCenterResponse response = webClient.get()
                .uri(url)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .bodyToMono(YouthCenterResponse.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(ex -> Mono.just(new YouthCenterResponse())) //실패 시 빈 빈
                .block();

        int totalCount = 0;
        List<SupportDTO> list = List.of();

        if(response != null && response.getResult() != null)
        {
            if(response.getResult().getPagging() != null)
            {
                totalCount = response.getResult().getPagging().getTotCount();
            }
            if(response.getResult().getYouthPolicyList() != null)
            {
                list = response.getResult().getYouthPolicyList().stream()
                        .map(p -> new SupportDTO(p.getPlcyNm(), p.getAplyUrlAddr(), p.getPlcyKywdNm()))
                        .toList();
            }
        }
        return new FetchResult(totalCount, new SupportListDTO(new ArrayList<>(list)));
    }

    @Getter
    @AllArgsConstructor
    public static class FetchResult {
        private final int totCount;
        private final SupportListDTO dto;
    }
}
