package SDD.smash.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 캐시 조회 결과를 세는 계측기.
 *
 * <p>이 프로젝트의 캐시는 전부 <b>파생 캐시</b>다. 미스는 정상이고(재계산하면 된다),
 * 문제는 <b>히트율이 낮은 것</b>과 <b>error 가 섞이는 것</b>이다. 그래서 미스와 에러를
 * 구분해 센다 - 캐시 어댑터는 Redis 장애를 미스로 흡수해 응답을 살리는 구조라,
 * 구분하지 않으면 "Redis 가 죽어서 미스"인 상황이 "캐시가 비어서 미스"와 똑같이 보인다.
 *
 * <p>메트릭: {@code smash_cache_lookups_total{cache, result}} (result = hit|miss|error)
 *
 * <p>호출 위치는 {@code infrastructure/cache} 어댑터다. 도메인·애플리케이션 계층은
 * 이 클래스를 알지 못한다(계측은 기술 관심사다).
 */
@Component
@RequiredArgsConstructor
public class CacheMetrics {

    private static final String LOOKUPS = "smash.cache.lookups";
    private static final String DESCRIPTION = "캐시 조회 횟수. result=hit|miss|error";

    private final MeterRegistry registry;

    /** 캐시에 값이 있었다. */
    public void hit(String cache) {
        count(cache, "hit");
    }

    /** 캐시가 비어 있었다. 정상 흐름이다. */
    public void miss(String cache) {
        count(cache, "miss");
    }

    /** 캐시 조회가 예외로 실패했다. 호출부는 이를 미스로 흡수하지만 계측은 따로 남긴다. */
    public void error(String cache) {
        count(cache, "error");
    }

    private void count(String cache, String result) {
        Counter.builder(LOOKUPS)
                .description(DESCRIPTION)
                .tag("cache", cache)
                .tag("result", result)
                .register(registry)
                .increment();
    }
}
