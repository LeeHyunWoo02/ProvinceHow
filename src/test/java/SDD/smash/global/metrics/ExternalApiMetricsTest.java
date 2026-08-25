package SDD.smash.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final ExternalApiMetrics metrics = new ExternalApiMetrics(registry);

    @Test
    @DisplayName("성공/실패/미호출이 outcome 태그로 구분돼 누적된다")
    void countsEachOutcomeUnderItsOwnTag() {
        metrics.success("youthcenter");
        metrics.failure("youthcenter");
        metrics.failure("youthcenter");
        metrics.skipped("youthcenter");

        assertThat(count("youthcenter", "success")).isEqualTo(1.0d);
        assertThat(count("youthcenter", "failure")).isEqualTo(2.0d);
        assertThat(count("youthcenter", "skipped")).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("수집원이 다르면 서로 다른 시계열로 집계된다")
    void separatesSeriesByApiName() {
        metrics.success("saramin");
        metrics.success("localdata");
        metrics.success("localdata");

        assertThat(count("saramin", "success")).isEqualTo(1.0d);
        assertThat(count("localdata", "success")).isEqualTo(2.0d);
    }

    /**
     * 실패 사유별로 태그를 더하고 싶은 유혹이 있지만, 태그 키가 갈리면 Prometheus 노출이 깨진다.
     * 이 테스트가 그 실수를 막는다.
     */
    @Test
    @DisplayName("모든 시계열이 같은 태그 키(api, outcome)를 갖는다")
    void keepsTagKeysIdenticalAcrossSeries() {
        metrics.success("kosis");
        metrics.failure("molit");
        metrics.skipped("youthcenter");

        assertThat(registry.find("smash.external.api.calls").counters())
                .isNotEmpty()
                .allSatisfy(counter -> assertThat(counter.getId().getTags())
                        .extracting("key")
                        .containsExactly("api", "outcome"));
    }

    private double count(String api, String outcome) {
        Counter counter = registry.find("smash.external.api.calls")
                .tag("api", api).tag("outcome", outcome).counter();
        return counter == null ? 0.0d : counter.count();
    }
}
