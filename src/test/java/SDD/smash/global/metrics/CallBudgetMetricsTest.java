package SDD.smash.global.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CallBudgetMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final CallBudgetMetrics metrics = new CallBudgetMetrics(registry);

    @Test
    @DisplayName("게이지는 등록 시점 값이 아니라 조회 시점의 공급자 값을 읽는다")
    void readsSupplierAtObservationTime() {
        AtomicInteger used = new AtomicInteger(0);
        metrics.register("localdata", used::get, () -> 9000);

        assertThat(gauge("smash.external.api.budget.used")).isEqualTo(0.0d);

        used.set(1234);

        // 게이지가 값을 복사해 뒀다면 여기서 0 이 나온다. 예산 소모는 계속 변하므로 이 성질이 필요하다.
        assertThat(gauge("smash.external.api.budget.used")).isEqualTo(1234.0d);
        assertThat(gauge("smash.external.api.budget.limit")).isEqualTo(9000.0d);
    }

    @Test
    @DisplayName("같은 수집원을 두 번 등록해도 게이지는 하나만 남는다")
    void registeringTwiceKeepsSingleGauge() {
        metrics.register("localdata", () -> 1, () -> 9000);
        metrics.register("localdata", () -> 2, () -> 9000);

        assertThat(registry.find("smash.external.api.budget.used").gauges()).hasSize(1);
        // 먼저 등록한 공급자가 남는다(Micrometer 의 등록 규칙).
        assertThat(gauge("smash.external.api.budget.used")).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("수집원마다 api 태그로 분리된다")
    void separatesGaugesByApiTag() {
        metrics.register("localdata", () -> 10, () -> 9000);
        metrics.register("saramin", () -> 20, () -> 500);

        assertThat(gauge("smash.external.api.budget.used", "localdata")).isEqualTo(10.0d);
        assertThat(gauge("smash.external.api.budget.used", "saramin")).isEqualTo(20.0d);
        assertThat(gauge("smash.external.api.budget.limit", "saramin")).isEqualTo(500.0d);
    }

    private double gauge(String name) {
        return gauge(name, "localdata");
    }

    private double gauge(String name, String api) {
        Gauge g = registry.find(name).tag("api", api).gauge();
        return g == null ? Double.NaN : g.value();
    }
}
