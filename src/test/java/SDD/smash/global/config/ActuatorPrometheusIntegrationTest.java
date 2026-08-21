package SDD.smash.global.config;

import SDD.smash.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관측성 설정의 계약을 검증한다.
 *
 * 검증 대상은 두 가지다.
 *  1) /actuator/prometheus 가 <b>관리 포트에서만</b> 응답한다 — 운영에서 compose 가 관리 포트를
 *     publish 하지 않으므로, 이 분리가 곧 액추에이터의 유일한 접근 통제다.
 *  2) 서비스 포트로 들어온 요청이 http.server.requests 히스토그램으로 잡힌다 — 대시보드의
 *     요청률/p95 패널이 이 메트릭 하나에 전부 걸려 있다.
 *
 * management.server.port=0 은 테스트에서 임의 포트를 받기 위한 것이고, 운영값은
 * application-prod.properties 의 MANAGEMENT_PORT(기본 8081) 다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // @SpringBootTest 는 테스트에서 메트릭 export 를 기본으로 끈다
                // (spring-boot-test 가 management.defaults.metrics.export.enabled=false 를 주입한다).
                // 끈 상태로는 PrometheusMeterRegistry 빈이 안 생기고 /actuator/prometheus 가 404 다.
                // 운영에는 해당되지 않는 테스트 전용 기본값이므로 여기서만 되살린다.
                // defaults 쪽은 "test" 프로퍼티 소스가 선점하므로 레지스트리별 키를 직접 켠다.
                "management.prometheus.metrics.export.enabled=true",
                "management.server.port=0",
                "management.endpoints.web.exposure.include=health,prometheus",
                "management.metrics.distribution.percentiles-histogram.http.server.requests=true"
        })
class ActuatorPrometheusIntegrationTest extends IntegrationTestSupport {

    /** 루트 URI 프리픽스 없이 절대 URL 로만 호출하기 위해 직접 만든다(포트가 두 개다). */
    private final TestRestTemplate rest = new TestRestTemplate();

    @Value("${local.server.port}")
    private int servicePort;

    @Value("${local.management.port}")
    private int managementPort;

    @Test
    @DisplayName("관리 포트의 /actuator/prometheus 가 메트릭을 노출한다")
    void prometheusEndpointIsServedOnManagementPort() {
        ResponseEntity<String> response = rest.getForEntity(managementUrl("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("서비스 포트로 들어온 요청이 http.server.requests 히스토그램으로 집계된다")
    void serviceRequestIsRecordedAsHttpServerRequestsHistogram() {
        // 상태코드는 단정하지 않는다. 빈 DB 라 200/500 이 갈릴 수 있지만 메트릭 집계는 어느 쪽이든 일어난다.
        rest.getForEntity(serviceUrl("/api/code/sido"), String.class);

        ResponseEntity<String> scrape = rest.getForEntity(managementUrl("/actuator/prometheus"), String.class);

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .contains("http_server_requests_seconds_bucket")
                .contains("uri=\"/api/code/sido\"");
    }

    @Test
    @DisplayName("서비스 포트로는 /actuator/prometheus 에 도달할 수 없다")
    void actuatorIsNotReachableOnTheServicePort() {
        ResponseEntity<String> response = rest.getForEntity(serviceUrl("/actuator/prometheus"), String.class);

        // 서비스 포트에는 액추에이터가 매핑되지 않으므로 404 다(시큐리티는 통과하지만 핸들러가 없다).
        // 본문은 비어 있을 수 있어 null 을 빈 문자열로 바꿔 단정한다.
        String body = response.getBody() == null ? "" : response.getBody();

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(body).doesNotContain("jvm_memory_used_bytes");
    }

    private String serviceUrl(String path) {
        return "http://localhost:" + servicePort + path;
    }

    private String managementUrl(String path) {
        return "http://localhost:" + managementPort + path;
    }
}
