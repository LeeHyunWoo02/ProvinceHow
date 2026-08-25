package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobScoreKey;
import SDD.smash.domain.job.domain.port.JobScoreCache;
import SDD.smash.global.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일자리 점수 캐시의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p>키·값 형식은 As-Is {@code JobScoreService} 와 <b>완전히 동일</b>하다.
 * 키는 {@code job:score:{중분류코드|default}}, 값은 Hash {@code {시군구코드(String): 점수(Integer)}},
 * TTL 은 12시간이다. {@code recommendation} 이관 전까지 옛 서비스와 같은 키를 공유하므로
 * 어느 한쪽이 쓴 캐시를 다른 쪽이 그대로 읽을 수 있어야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobScoreRedisAdapter implements JobScoreCache {

    private static final String KEY_PREFIX = "job:score:";

    /** 직종 코드가 없는(전체 일자리 기준) 키의 리터럴. As-Is 와 같은 문자열이어야 한다. */
    private static final String ALL_JOBS_KEY = "default";

    /** 원본이 시드 배치로만 바뀌므로 반나절이면 충분하다. */
    private static final Duration TTL = Duration.ofHours(12);

    /** {@code evictAll} 이 훑을 키 패턴. 한 번에 가져올 개수는 Redis 를 오래 잡지 않을 만큼만. */
    private static final String SCAN_PATTERN = KEY_PREFIX + "*";
    private static final int SCAN_COUNT = 500;

    private final RedisTemplate<String, Object> redisTemplate;

    /** 히트/미스/에러 계측. 캐시 동작 자체에는 관여하지 않는다. */
    private final CacheMetrics cacheMetrics;

    /** 메트릭의 cache 태그 값. Redis 키 네임스페이스와 같게 둬서 지표와 키를 바로 대조한다. */
    private static final String CACHE_NAME = "job:score";

    @Override
    public Optional<Map<SigunguCode, Score>> find(JobScoreKey key) {
        String redisKey = redisKey(key);
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(redisKey);
            if (cached == null || cached.isEmpty()) {
                cacheMetrics.miss(CACHE_NAME);
                return Optional.empty();
            }
            cacheMetrics.hit(CACHE_NAME);
            return Optional.of(toDomain(cached));
        } catch (RuntimeException e) {
            // 캐시 장애를 미스로 흡수한다. Redis 가 죽어도 재계산으로 응답할 수 있어야 한다.
            log.warn("[cache] 일자리 점수 조회 실패 key={} - 미스로 처리", redisKey, e);
            cacheMetrics.error(CACHE_NAME);
            return Optional.empty();
        }
    }

    @Override
    public void put(JobScoreKey key, Map<SigunguCode, Score> scores) {
        // 히트 판정이 "비어있지 않음"이므로 빈 결과는 저장하지 않는다.
        if (scores == null || scores.isEmpty()) {
            return;
        }
        String redisKey = redisKey(key);
        try {
            redisTemplate.opsForHash().putAll(redisKey, toRaw(scores));
            // Hash 의 putAll 에는 TTL 인자가 없다. expire 를 빠뜨리면 영구 키가 된다.
            redisTemplate.expire(redisKey, TTL);
        } catch (RuntimeException e) {
            log.warn("[cache] 일자리 점수 저장 실패 key={}", redisKey, e);
        }
    }

    /**
     * 일자리 수 원본이 갱신되면 파생 점수를 전부 버린다.
     *
     * <p>직종 중분류가 수백 개라 키를 정적으로 열거할 수 없다. 그래서 패턴 스캔이 필요한데,
     * {@code KEYS} 는 Redis 를 블로킹하는 O(N) 명령이므로 커서 기반 {@code SCAN} 을 쓴다.
     * 지우는 대상은 As-Is 의 {@code keys("job:score:*")} 와 같다.
     */
    @Override
    public void evictAll() {
        try {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(SCAN_PATTERN)
                    .count(SCAN_COUNT)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (keys.isEmpty()) {
                log.info("[cache] 삭제할 일자리 점수 캐시 없음");
                return;
            }
            Long deleted = redisTemplate.delete(keys);
            log.info("[cache] 일자리 점수 캐시 무효화 대상={}, 삭제={}", keys.size(), deleted);
        } catch (RuntimeException e) {
            log.warn("[cache] 일자리 점수 캐시 무효화 실패", e);
        }
    }

    private String redisKey(JobScoreKey key) {
        return KEY_PREFIX + (key.isAllJobs() ? ALL_JOBS_KEY : key.jobCode().value());
    }

    /** Redis Hash → 도메인 타입. 값은 Integer 로 저장돼 있다. */
    private Map<SigunguCode, Score> toDomain(Map<Object, Object> cached) {
        Map<SigunguCode, Score> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : cached.entrySet()) {
            result.put(SigunguCode.of((String) entry.getKey()),
                    Score.of(((Number) entry.getValue()).intValue()));
        }
        return result;
    }

    /** 도메인 타입 → Redis Hash. 옛 서비스가 읽을 수 있도록 원시 String/Integer 로 저장한다. */
    private Map<String, Integer> toRaw(Map<SigunguCode, Score> scores) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        scores.forEach((code, score) -> raw.put(code.value(), score.value()));
        return raw;
    }
}
