package SDD.smash.domain.infra.infrastructure.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * LOCALDATA(공공데이터포털 인허가 API) 전용 HTTP 클라이언트 빈.
 *
 * <h2>왜 공유 빈을 쓰지 않는가</h2>
 * {@code global/config/RestClientConfig} 의 공유 {@code restClient}(connect 5s / read 25s)은
 * MOLIT·KOSIS·워크넷·사람인, 그리고 같은 LOCALDATA 도메인의 {@code LocalDataBulkCsvAdapter} 가
 * 함께 쓴다. 벌크 CSV 는 자치단체·업종당 <b>요청 1회로 완결</b>이라 페이징 누적이 없어
 * 공유 빈(read 25s)으로 충분하다. 인허가 API 는 한 대상(지역×업종)당 페이지를 끝까지 받아야 하고
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
 * 같은 타입 빈이 둘이므로 이 빈은 {@code defaultCandidate = false} 로 등록한다 —
 * <b>{@code @Qualifier} 로 이름을 지목한 곳(=LocalDataApiAdapter)에만</b> 주입되고, 타입만 보고
 * 주입하는 다른 어댑터의 후보에는 아예 오르지 않는다.
 * 공유 빈은 {@code @Primary} 라 이 속성을 빠뜨려도 타입 주입이 모호해지지 않는다.
 */
@Configuration
public class LocalDataHttpClientConfig {

    /** 이 빈을 주입받을 때 쓰는 한정자. 어댑터의 {@code @Qualifier} 와 한 쌍이다. */
    public static final String LOCALDATA_REST_CLIENT = "localDataRestClient";

    @Bean(name = LOCALDATA_REST_CLIENT, defaultCandidate = false)
    public RestClient localDataRestClient(
            @Value("${apis.localdata.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${apis.localdata.read-timeout-ms:60000}") long readTimeoutMs) {

        return RestClient.builder()
                .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1L, connectTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1L, readTimeoutMs)));
        return factory;
    }
}
