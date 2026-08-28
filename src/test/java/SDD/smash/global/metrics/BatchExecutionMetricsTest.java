package SDD.smash.global.metrics;

import SDD.smash.global.batch.launch.BatchGuard;
import SDD.smash.global.batch.launch.RunningJobExecution;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BatchExecutionMetricsTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock BatchGuard batchGuard;

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MovableClock clock = new MovableClock(Instant.parse("2026-08-25T03:00:00Z"));

    @Test
    @DisplayName("가장 오래된 실행의 나이를 시작 시각 기준으로 노출한다")
    void exposesOldestRunningAgeFromStartTime() {
        given(batchGuard.findRunningExecutions()).willReturn(List.of(
                running(1L, "infraJob", Duration.ofHours(6), Duration.ofMinutes(2)),
                running(2L, "infraJob", Duration.ofHours(2), Duration.ofMinutes(1))));

        metrics("infraJob", "dwellingJob");

        assertThat(gauge("smash.batch.running.executions", "infraJob")).isEqualTo(2.0d);
        assertThat(gauge("smash.batch.oldest.running.age.seconds", "infraJob"))
                .isEqualTo((double) Duration.ofHours(6).toSeconds());
        // 실행 이력이 없는 Job 도 0 으로 보인다 - 계열이 사라지면 경보를 걸 수 없다.
        assertThat(gauge("smash.batch.running.executions", "dwellingJob")).isEqualTo(0.0d);
    }

    @Test
    @DisplayName("스크랩마다 메타 DB 를 읽지 않도록 30초 동안 스냅샷을 재사용한다")
    void cachesSnapshotForThirtySeconds() {
        given(batchGuard.findRunningExecutions())
                .willReturn(List.of(running(1L, "infraJob", Duration.ofHours(1), Duration.ofMinutes(1))))
                .willReturn(List.of());

        metrics("infraJob");

        assertThat(gauge("smash.batch.running.executions", "infraJob")).isEqualTo(1.0d);
        clock.advance(Duration.ofSeconds(29));
        assertThat(gauge("smash.batch.running.executions", "infraJob")).isEqualTo(1.0d);

        clock.advance(Duration.ofSeconds(2));
        assertThat(gauge("smash.batch.running.executions", "infraJob")).isEqualTo(0.0d);
    }

    @Test
    @DisplayName("정리 횟수와 기동 건너뜀 횟수를 사유별로 센다")
    void countsRecoveriesAndSkips() {
        BatchExecutionMetrics metrics = metrics();

        metrics.staleRecovered("infraJob");
        metrics.launchSkipped("infraJob", "running");
        metrics.launchSkipped("infraJob", "running");
        metrics.launchSkipped("seedMasterJob", "launch_failed");

        assertThat(registry.find("smash.batch.stale.recovered").tag("job", "infraJob").counter().count())
                .isEqualTo(1.0d);
        assertThat(registry.find("smash.batch.launch.skipped")
                .tags("job", "infraJob", "reason", "running").counter().count()).isEqualTo(2.0d);
        assertThat(registry.find("smash.batch.launch.skipped")
                .tags("job", "seedMasterJob", "reason", "launch_failed").counter().count()).isEqualTo(1.0d);
    }

    private BatchExecutionMetrics metrics(String... jobNames) {
        return new BatchExecutionMetrics(registry, batchGuard, clock, List.of(jobNames));
    }

    private RunningJobExecution running(long id, String jobName, Duration since, Duration idle) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new RunningJobExecution(id, jobName, now.minus(since), now.minus(idle));
    }

    private double gauge(String name, String job) {
        Gauge gauge = registry.find(name).tag("job", job).gauge();
        return gauge == null ? Double.NaN : gauge.value();
    }

    /** 캐시 만료를 검증하려면 시간이 흘러야 한다. */
    private static final class MovableClock extends Clock {

        private Instant instant;

        private MovableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
