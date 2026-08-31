package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.domain.job.domain.model.NonCapitalRatioSnapshot;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.domain.port.NonCapitalRatioCache;
import SDD.smash.global.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 비수도권 구인배수 분포 캐시의 <b>프로세스 메모리</b> 구현.
 *
 * <p>Redis 를 쓰지 않는다. 이 캐시가 담는 것은 시군구-실수 맵을 직종별로 다시 감싼 중첩 구조라
 * "값은 원시 타입으로 저장한다"(redis-conventions §3-6)는 규칙과 맞지 않고, 담을 항목이
 * 최신월 한 건뿐이라 네트워크 왕복을 더할 이유가 없다. 인스턴스가 하나뿐인 배포 형태라
 * 프로세스마다 따로 계산돼도 결과가 갈리지 않는다(같은 DB 를 같은 규칙으로 접는다).
 *
 * <p>유효성은 <b>기준월</b>이 판정한다. 새 달이 적재되면 키가 달라져 자동으로 미스가 된다.
 * TTL 은 배치를 거치지 않은 수동 보정(이관 UPDATE 등)까지 덮기 위한 안전망이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NonCapitalRatioMemoryAdapter implements NonCapitalRatioCache {

    private static final Duration TTL = Duration.ofHours(6);
    private static final String CACHE_NAME = "job:noncapital-ratio";

    private final CacheMetrics cacheMetrics;

    private final AtomicReference<Entry> store = new AtomicReference<>();

    @Override
    public Optional<NonCapitalRatioSnapshot> find(StatisticsMonth month) {
        Entry entry = store.get();
        if (month == null || entry == null
                || !entry.snapshot.month().equals(month)
                || entry.isExpired()) {
            cacheMetrics.miss(CACHE_NAME);
            return Optional.empty();
        }
        cacheMetrics.hit(CACHE_NAME);
        return Optional.of(entry.snapshot);
    }

    @Override
    public void put(NonCapitalRatioSnapshot snapshot) {
        // 히트 판정이 "비어있지 않음" 과 대칭이어야 하므로 빈 분포는 담지 않는다.
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        store.set(new Entry(snapshot, Instant.now().plus(TTL)));
    }

    @Override
    public void evictAll() {
        store.set(null);
        log.info("[cache] 비수도권 구인배수 분포 캐시 무효화");
    }

    private record Entry(NonCapitalRatioSnapshot snapshot, Instant expiresAt) {

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
