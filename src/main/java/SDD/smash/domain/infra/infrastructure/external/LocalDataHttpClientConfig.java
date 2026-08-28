package SDD.smash.domain.infra.infrastructure.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * LOCALDATA(공공데이터포털 인허가 API) 전용 HTTP 클라이언트 빈.
 *
 * <h2>왜 공유 빈을 쓰지 않는가</h2>
 * {@code global/config/RestClientConfig} 의 공유 {@code restClient}(connect 5s / read 25s)은
 * MOLIT·KOSIS·사람인이 함께 쓴다. 인허가 API 는 한 대상(지역×업종)당 페이지를 끝까지 받아야 하고
 * 서버가 느려지면 25초 안에 응답이 오지 않아 {@code ResourceAccessException} 으로 전량 폐기되는
 * 장애가 실제로 있었다(2026-08, {@code infraStep} 18분 55초 후 실패). 읽기 타임아웃을 이 경로에서만
 * 늘리기 위해 전용 빈을 둔다. <b>공유 빈은 건드리지 않는다.</b>
 *
 * <h2>왜 global/config 가 아닌가</h2>
 * 이 빈의 수명주기와 설정값은 {@code infra} 컨텍스트의 외부 어댑터 하나에만 묶여 있다.
 * architecture-conventions §3 에서 {@code global/config} 는 컨텍스트에 속하지 않는 애플리케이션
 * 부트스트랩이고, 컨텍스트 고유의 기술 상세는 그 컨텍스트의 {@code infrastructure} 계층이다.
 *
 * <h2>주입 모호성</h2>
 * 공유 빈이 {@code @Primary} 가 아니므로 같은 타입 빈이 둘이 되면 다른 어댑터의 주입이 모호해질 수
 * 있다. 그래서 이 빈들은 {@code defaultCandidate = false} 로 등록한다 — <b>{@code @Qualifier} 로
 * 이름을 지목한 곳(=LocalDataApiAdapter)에만</b> 주입되고, 타입만 보고 주입하는 다른 어댑터의
 * 후보에는 아예 오르지 않는다.
 *
 * <h2>RestTemplate 빈이 아직 남아 있는 이유</h2>
 * RestTemplate → RestClient 이전이 2차로 나뉘어 있다. 남은 어댑터를 옮기기 전까지 두 종류가
 * 공존해야 각 커밋이 컴파일·테스트를 통과한다. 이전이 끝나면 {@code localDataRestTemplate} 을 지운다.
 */
@Configuration
public class LocalDataHttpClientConfig {

    /** 이 빈을 주입받을 때 쓰는 한정자. 어댑터의 {@code @Qualifier} 와 한 쌍이다. */
    public static final String LOCALDATA_REST_CLIENT = "localDataRestClient";

    /** 이전이 끝나면 제거될 구 클라이언트의 한정자. */
    public static final String LOCALDATA_REST_TEMPLATE = "localDataRestTemplate";

    @Bean(name = LOCALDATA_REST_CLIENT, defaultCandidate = false)
    public RestClient localDataRestClient(
            @Value("${apis.localdata.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${apis.localdata.read-timeout-ms:60000}") long readTimeoutMs) {

        return RestClient.builder()
                .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                .build();
    }

    @Bean(name = LOCALDATA_REST_TEMPLATE, defaultCandidate = false)
    public RestTemplate localDataRestTemplate(
            @Value("${apis.localdata.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${apis.localdata.read-timeout-ms:60000}") long readTimeoutMs) {

        return new RestTemplate(requestFactory(connectTimeoutMs, readTimeoutMs));
    }

    private static SimpleClientHttpRequestFactory requestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1L, connectTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1L, readTimeoutMs)));
        return factory;
    }
}
