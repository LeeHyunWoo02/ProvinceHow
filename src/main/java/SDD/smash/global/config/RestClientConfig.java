package SDD.smash.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 앱 전역 공유 {@link RestClient} 빈. 외부 HTTP 어댑터가 타입으로 주입받는다.
 *
 * <p>Spring 6.1(Boot 3.2+)부터 동기 호출의 권장 클라이언트가 {@code RestClient} 다.
 * 타임아웃은 connect 5s / read 25s 다.
 * 더 긴 읽기 타임아웃이 필요한 경로는 컨텍스트별 전용 빈을 따로 둔다
 * (infra 의 {@code LocalDataHttpClientConfig}).
 */
@Configuration
public class RestClientConfig {

    // @Primary: 전용 RestClient 빈이 늘어날 때 defaultCandidate=false 를 빠뜨려도
    // 타입 주입하는 어댑터 8개가 한꺼번에 부팅 실패하지 않게 하는 안전판이다.
    @Bean
    @Primary
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        return RestClient.builder().requestFactory(factory).build();
    }
}
