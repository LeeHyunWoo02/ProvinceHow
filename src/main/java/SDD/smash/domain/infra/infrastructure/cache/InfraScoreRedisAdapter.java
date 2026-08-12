package SDD.smash.domain.infra.infrastructure.cache;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.domain.model.InfraScoreKey;
import SDD.smash.domain.infra.domain.port.InfraScoreCache;
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
 * 인프라 점수 캐시의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p>키·값 형식은 As-Is {@code InfraScoreService} 와 <b>완전히 동일</b>하다.
 * 키는 {@code infra:score:{infraChoice}}(정수 그대로, {@code null} 이면 문자열 "null"),
 * 값은 Hash {@code {시군구코드(String): 점수(Integer)}}, TTL 은 24시간이다.
 * {@code recommendation} 이관 전까지 옛 서비스와 같은 키를 공유하므로
 * 어느 한쪽이 쓴 캐시를 다른 쪽이 그대로 읽을 수 있어야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InfraScoreRedisAdapter implements InfraScoreCache {

    private static final String KEY_PREFIX = "infra:score:";

    /** 원본이 시드 배치로만 바뀌므로 하루면 충분하다. */
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * {@code infraChoice} 는 4비트(0~15)라 유효 키가 유한하다.
     * {@code null} 선택({@code infraChoice} 미지정)은 서비스가 캐시에 쓰지 않으므로
     * ("선택 없음" 분기가 캐시 저장보다 먼저 반환한다) 열거 대상에 넣지 않는다.
     */
    private static final int MIN_CHOICE = 0;
    private static final int MAX_CHOICE = 15;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Optional<Map<SigunguCode, Score>> find(InfraScoreKey key) {
        String redisKey = redisKey(key);
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(redisKey);
            if (cached == null || cached.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toDomain(cached));
        } catch (RuntimeException e) {
            // 캐시 장애를 미스로 흡수한다. Redis 가 죽어도 재계산으로 응답할 수 있어야 한다.
            log.warn("[cache] 인프라 점수 조회 실패 key={} - 미스로 처리", redisKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(InfraScoreKey key, Map<SigunguCode, Score> scores) {
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
            log.warn("[cache] 인프라 점수 저장 실패 key={}", redisKey, e);
        }
    }

    /**
     * 인프라 원본이 갱신되면 파생 점수를 전부 버린다.
     *
     * <p>{@code infraChoice} 가 0~15 로 유한하므로 {@code KEYS}/{@code SCAN} 패턴 매칭 없이
     * 16개 키를 직접 열거해 지운다. (15 보다 큰 마스크를 던지는 호출은 As-Is 에도 없었다 —
     * UI 선택지가 4개뿐이라 실제로는 발생하지 않는다. 발생한다면 그 키는 이 열거에 없어
     * TTL 이 지나야 사라진다. 고치지 않는다 — 동작 무변경.)
     */
    @Override
    public void evictAll() {
        List<String> keys = new ArrayList<>();
        for (int choice = MIN_CHOICE; choice <= MAX_CHOICE; choice++) {
            keys.add(KEY_PREFIX + choice);
        }
        try {
            Long deleted = redisTemplate.delete(keys);
            log.info("[cache] 인프라 점수 캐시 무효화 대상={}, 삭제={}", keys.size(), deleted);
        } catch (RuntimeException e) {
            log.warn("[cache] 인프라 점수 캐시 무효화 실패", e);
        }
    }

    private String redisKey(InfraScoreKey key) {
        return KEY_PREFIX + key.infraChoice();
    }

    /** Redis Hash → 도메인 타입. 값은 Integer 로 저장돼 있다. */
    private Map<SigunguCode, Score> toDomain(Map<Object, Object> cached) {
        Map<SigunguCode, Score> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : cached.entrySet()) {
            result.put(SigunguCode.of(String.valueOf(entry.getKey())),
                    Score.of(Integer.parseInt(entry.getValue().toString())));
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
