package SDD.smash.domain.support.infrastructure.cache;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 지원정책 정본 저장소의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p><b>키 네임스페이스가 As-Is 와 다르다.</b> As-Is 는 {@code sigunguCode:tag}/
 * {@code sigunguCode:tag:NUM} 처럼 접두어가 없었다(redis-conventions §4.1 이 지목한 문제).
 * 여기서는 {@code support:policy:} 접두어를 붙였고, 생산자(리프레시 유스케이스)와
 * 소비자(조회 유스케이스)를 동시에 이 네임스페이스로 옮겼다 — 옛 스케줄러/서비스는
 * 계속 옛 네임스페이스를 쓰므로 옛/새 데이터는 서로 다른 키 공간에 존재한다.
 *
 * <p>정본 저장소이므로 캐시처럼 조회 실패를 흡수하지 않는다("데이터 없음"과
 * "저장소 장애"를 구분해야 하므로 예외를 그대로 흘려보낸다 — redis-conventions §6.3).
 * 값이 없는 것은 실패가 아니라 "정책이 없다"는 정상 상태다.
 */
@Component
@RequiredArgsConstructor
public class SupportPolicyRedisAdapter implements SupportPolicyRepository {

    private static final String KEY_PREFIX = "support:policy:";

    /** 원본 갱신 주기(3일) + 1일(실패 유예). */
    private static final Duration TTL = Duration.ofDays(4);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, SupportPolicyListPayload> supportListRedisTemplate;

    @Override
    public List<SupportPolicy> findBy(SigunguCode code, SupportTag tag) {
        SupportPolicyListPayload payload = supportListRedisTemplate.opsForValue().get(listKey(code, tag));
        if (payload == null || payload.getPolicies() == null) {
            return List.of();
        }
        return payload.getPolicies().stream().map(this::toDomain).toList();
    }

    @Override
    public int countBy(SigunguCode code, SupportTag tag) {
        Object value = redisTemplate.opsForValue().get(countKey(code, tag));
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    @Override
    public void saveAll(SigunguCode code, SupportTag tag, List<SupportPolicy> policies) {
        SupportPolicyListPayload payload = new SupportPolicyListPayload();
        payload.setPolicies(policies.stream().map(this::toPayload).toList());

        supportListRedisTemplate.opsForValue().set(listKey(code, tag), payload, TTL);
        redisTemplate.opsForValue().set(countKey(code, tag), policies.size(), TTL);
    }

    /** {@code tag.name()} 을 쓴다. 옛 네임스페이스는 한글 라벨({@code tag.getValue()})을 그대로 키에 넣었다. */
    private String listKey(SigunguCode code, SupportTag tag) {
        return KEY_PREFIX + code.value() + ":" + tag.name();
    }

    private String countKey(SigunguCode code, SupportTag tag) {
        return listKey(code, tag) + ":count";
    }

    private SupportPolicy toDomain(SupportPolicyPayload payload) {
        return new SupportPolicy(payload.getName(), payload.getApplyUrl(), payload.getKeyword());
    }

    private SupportPolicyPayload toPayload(SupportPolicy policy) {
        return new SupportPolicyPayload(policy.name(), policy.applyUrl(), policy.keyword());
    }
}
