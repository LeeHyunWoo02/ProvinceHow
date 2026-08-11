package SDD.smash.dwelling.infrastructure.cache;

import SDD.smash.common.domain.model.Money;
import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.domain.model.DwellingScoreKey;
import SDD.smash.dwelling.domain.model.DwellingType;
import SDD.smash.dwelling.domain.port.DwellingScoreCache;
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
 * 주거 점수 캐시의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p>키·값 형식은 As-Is {@code DwellingScoreSerivce} 와 <b>완전히 동일</b>하다.
 * 키는 {@code dwelling:score:{TYPE}:{보정예산}}, 값은 Hash {@code {시군구코드(String): 점수(Integer)}},
 * TTL 은 30일이다. {@code recommendation} 이관 전까지 옛 서비스와 같은 키를 공유하므로
 * 어느 한쪽이 쓴 캐시를 다른 쪽이 그대로 읽을 수 있어야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DwellingScoreRedisAdapter implements DwellingScoreCache {

    /** As-Is 는 접두어에 콜론이 없어 사용처에서 덧붙였다. 접두어에 포함해 통일한다(키 문자열은 동일). */
    private static final String KEY_PREFIX = "dwelling:score:";

    /** 원본은 월 단위 실거래라 한 달이면 충분하다. */
    private static final Duration TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Optional<Map<SigunguCode, Score>> find(DwellingScoreKey key) {
        String redisKey = redisKey(key);
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(redisKey);
            if (cached == null || cached.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toDomain(cached));
        } catch (RuntimeException e) {
            // 캐시 장애를 미스로 흡수한다. Redis 가 죽어도 재계산으로 응답할 수 있어야 한다.
            log.warn("[cache] 주거 점수 조회 실패 key={} - 미스로 처리", redisKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(DwellingScoreKey key, Map<SigunguCode, Score> scores) {
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
            log.warn("[cache] 주거 점수 저장 실패 key={}", redisKey, e);
        }
    }

    /**
     * 시세 원본이 갱신되면 파생 점수를 전부 버린다.
     *
     * <p>예산이 구간화돼 키가 유한하므로({@code DwellingType.allNormalizedBudgets})
     * 전부 열거해 지운다. {@code KEYS} 패턴 스캔은 Redis 를 블로킹하는 O(N) 명령이라 쓰지 않는다.
     */
    @Override
    public void evictAll() {
        List<String> keys = new ArrayList<>();
        for (DwellingType type : DwellingType.values()) {
            for (Money budget : type.allNormalizedBudgets()) {
                keys.add(redisKey(new DwellingScoreKey(type, budget)));
            }
        }
        try {
            Long deleted = redisTemplate.delete(keys);
            log.info("[cache] 주거 점수 캐시 무효화 대상={}, 삭제={}", keys.size(), deleted);
        } catch (RuntimeException e) {
            log.warn("[cache] 주거 점수 캐시 무효화 실패", e);
        }
    }

    private String redisKey(DwellingScoreKey key) {
        return KEY_PREFIX + key.type().name() + ":" + key.normalizedBudget().manwon();
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
