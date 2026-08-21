package SDD.smash.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final CacheMetrics metrics = new CacheMetrics(registry);

    @Test
    @DisplayName("히트/미스/에러가 result 태그로 구분돼 누적된다")
    void countsEachResultUnderItsOwnTag() {
        metrics.hit("job:score");
        metrics.hit("job:score");
        metrics.miss("job:score");
        metrics.error("job:score");

        assertThat(count("job:score", "hit")).isEqualTo(2.0d);
        assertThat(count("job:score", "miss")).isEqualTo(1.0d);
        assertThat(count("job:score", "error")).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("캐시 이름이 다르면 서로 다른 시계열로 집계된다")
    void separatesSeriesByCacheName() {
        metrics.hit("job:score");
        metrics.hit("dwelling:score");
        metrics.hit("dwelling:score");

        assertThat(count("job:score", "hit")).isEqualTo(1.0d);
        assertThat(count("dwelling:score", "hit")).isEqualTo(2.0d);
    }

    /**
     * Prometheus 노출 시 같은 이름의 메트릭은 태그 <b>키</b> 집합이 같아야 한다.
     * 다르면 Micrometer 가 스크랩 시점에 거부하므로 계약으로 고정한다.
     */
    @Test
    @DisplayName("모든 시계열이 같은 태그 키(cache, result)를 갖는다")
    void keepsTagKeysIdenticalAcrossSeries() {
        metrics.hit("job:score");
        metrics.miss("dwelling:score");
        metrics.error("infra:score");

        assertThat(registry.find("smash.cache.lookups").counters())
                .isNotEmpty()
                .allSatisfy(counter -> assertThat(counter.getId().getTags())
                        .extracting("key")
                        .containsExactly("cache", "result"));
    }

    private double count(String cache, String result) {
        Counter counter = registry.find("smash.cache.lookups")
                .tag("cache", cache).tag("result", result).counter();
        return counter == null ? 0.0d : counter.count();
    }
}
