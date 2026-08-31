package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.domain.job.domain.model.NonCapitalRatioSnapshot;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.CacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NonCapitalRatioMemoryAdapterTest {

    private static final StatisticsMonth JULY = StatisticsMonth.of("2026-07");
    private static final StatisticsMonth AUGUST = StatisticsMonth.of("2026-08");

    private final NonCapitalRatioMemoryAdapter adapter =
            new NonCapitalRatioMemoryAdapter(new CacheMetrics(new SimpleMeterRegistry()));

    @Test
    @DisplayName("담아 둔 기준월로 조회하면 그대로 돌아온다")
    void returnsStoredSnapshotForSameMonth() {
        adapter.put(snapshot(JULY));

        assertThat(adapter.find(JULY)).isPresent();
    }

    @Test
    @DisplayName("기준월이 바뀌면 미스다 — 새 달이 적재되면 자동으로 다시 계산된다")
    void missesWhenMonthChanged() {
        adapter.put(snapshot(JULY));

        assertThat(adapter.find(AUGUST)).isEmpty();
    }

    @Test
    @DisplayName("빈 분포는 담지 않는다")
    void doesNotStoreEmptySnapshot() {
        adapter.put(NonCapitalRatioSnapshot.empty(JULY));

        assertThat(adapter.find(JULY)).isEmpty();
    }

    @Test
    @DisplayName("무효화하면 비워진다")
    void evictsStoredSnapshot() {
        adapter.put(snapshot(JULY));

        adapter.evictAll();

        assertThat(adapter.find(JULY)).isEmpty();
    }

    private NonCapitalRatioSnapshot snapshot(StatisticsMonth month) {
        return NonCapitalRatioSnapshot.of(month, Map.of(SigunguCode.of("46110"), 0.3), Map.of());
    }
}
