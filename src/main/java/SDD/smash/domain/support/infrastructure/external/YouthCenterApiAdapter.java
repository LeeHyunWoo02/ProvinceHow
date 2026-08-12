package SDD.smash.domain.support.infrastructure.external;

import SDD.smash.global.config.YouthCenterProperties;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 청년정책 API 어댑터. {@code SupportPolicyProvider} 포트 구현이다.
 * As-Is {@code YouthCenterClient} 를 옮긴 것이며(architecture-conventions §8 — 이름 정정),
 * URL 조립·타임아웃·실패 시 빈 응답 처리를 그대로 유지했다.
 *
 * <p>외부 API 어휘({@code zipCd}, {@code plcyKywdNm}, {@code apiKeyNm})는
 * 이 클래스 밖으로 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YouthCenterApiAdapter implements SupportPolicyProvider {

    private final WebClient webClient;
    private final YouthCenterProperties properties;

    @Override
    public List<SupportPolicy> fetch(SigunguCode code, SupportTag tag) {

        String url = properties.getPath()
                + "?apiKeyNm=" + properties.getApiKey()
                + "&pageNum=1"
                + "&pageSize=100"
                + "&rtnType=json"
                + "&zipCd=" + code.value()
                + "&plcyKywdNm=" + tag.getValue();

        YouthCenterApiResponse response = webClient.get()
                .uri(url)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .bodyToMono(YouthCenterApiResponse.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(ex -> {
                    log.warn("[YouthCenter] fetch 실패 sigungu={}, tag={}", code.value(), tag, ex);
                    return Mono.just(new YouthCenterApiResponse());
                })
                .block();

        if (response == null || response.getResult() == null
                || response.getResult().getYouthPolicyList() == null) {
            return List.of();
        }

        return response.getResult().getYouthPolicyList().stream()
                .map(p -> new SupportPolicy(p.getPlcyNm(), p.getAplyUrlAddr(), p.getPlcyKywdNm()))
                .toList();
    }
}
