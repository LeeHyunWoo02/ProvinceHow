package SDD.smash.domain.support.infrastructure.cache;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportScoreKey;
import SDD.smash.domain.support.domain.port.SupportScoreCache;
import SDD.smash.global.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 지원정책 점수 캐시의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p>키·값 형식은 As-Is {@code SupportScoreService} 와 <b>완전히 동일</b>하다
 * (이 캐시는 redis-conventions §4.1 표에도 이미 {@code support:score:*} 로 네임스페이스가
 * 있었으므로 재작명 대상이 아니다 — 정책 원본 키만 새 네임스페이스로 바뀐다).
 * 키는 {@code support:score:{supportChoice}}(정수 그대로, {@code null} 이면 문자열 "null"),
 * 값은 Hash {@code {시군구코드(String): 점수(Integer)}}, TTL 은 4일이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupportScoreRedisAdapter implements SupportScoreCache {

    private static final String KEY_PREFIX = "support:score:";

    /** 원본(지원정책) 이 3일 주기로 갱신되므로 그보다 하루 긴 4일이면 충분하다. */
    private static final Duration TTL = Duration.ofDays(4);

    /** {@code supportChoice} 는 4비트(0~15)라 유효 키가 유한하다. infra 와 같은 전략이다. */
    private static final int MIN_CHOICE = 0;
    private static final int MAX_CHOICE = 15;

    private final RedisTemplate<String, Object> redisTemplate;

    /** 히트/미스/에러 계측. 캐시 동작 자체에는 관여하지 않는다. */
    private final CacheMetrics cacheMetrics;

    /** 메트릭의 cache 태그 값. Redis 키 네임스페이스와 같게 둬서 지표와 키를 바로 대조한다. */
    private static final String CACHE_NAME = "support:score";

    @Override
    public Optional<Map<SigunguCode, Score>> find(SupportScoreKey key) {
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
            log.warn("[cache] 지원정책 점수 조회 실패 key={} - 미스로 처리", redisKey, e);
            cacheMetrics.error(CACHE_NAME);
            return Optional.empty();
        }
    }

    @Override
    public void put(SupportScoreKey key, Map<SigunguCode, Score> scores) {
        if (scores == null || scores.isEmpty()) {
            return;
        }
        String redisKey = redisKey(key);
        try {
            redisTemplate.opsForHash().putAll(redisKey, toRaw(scores));
            redisTemplate.expire(redisKey, TTL);
        } catch (RuntimeException e) {
            log.warn("[cache] 지원정책 점수 저장 실패 key={}", redisKey, e);
        }
    }

    /**
     * 지원정책 원본이 갱신되면 파생 점수를 전부 버린다.
     * {@code supportChoice} 가 0~15 로 유한하므로 16개 키를 직접 열거해 지운다.
     */
    @Override
    public void evictAll() {
        List<String> keys = new ArrayList<>();
        for (int choice = MIN_CHOICE; choice <= MAX_CHOICE; choice++) {
            keys.add(KEY_PREFIX + choice);
        }
        try {
            Long deleted = redisTemplate.delete(keys);
            log.info("[cache] 지원정책 점수 캐시 무효화 대상={}, 삭제={}", keys.size(), deleted);
        } catch (RuntimeException e) {
            log.warn("[cache] 지원정책 점수 캐시 무효화 실패", e);
        }
    }

    private String redisKey(SupportScoreKey key) {
        return KEY_PREFIX + key.supportChoice();
    }

    private Map<SigunguCode, Score> toDomain(Map<Object, Object> cached) {
        Map<SigunguCode, Score> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : cached.entrySet()) {
            result.put(SigunguCode.of(String.valueOf(entry.getKey())),
                    Score.of(Integer.parseInt(entry.getValue().toString())));
        }
        return result;
    }

    private Map<String, Integer> toRaw(Map<SigunguCode, Score> scores) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        scores.forEach((code, score) -> raw.put(code.value(), score.value()));
        return raw;
    }
}
