package SDD.smash.domain.support.infrastructure.cache;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 지원정책 정본 저장소의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p><b>키 네임스페이스가 As-Is 와 다르다.</b> As-Is 는 {@code sigunguCode:tag}/
 * {@code sigunguCode:tag:NUM} 처럼 접두어가 없었다(redis-conventions §4.1 이 지목한 문제).
 * 여기서는 {@code support:policy:} 접두어를 붙였고, 생산자(리프레시 유스케이스)와
 * 소비자(조회 유스케이스)를 동시에 이 네임스페이스로 옮겼다.
 *
 * <h2>정본에는 TTL 을 걸지 않는다</h2>
 * 이 어댑터는 파생 캐시가 아니라 <b>정본</b>이다. 만료를 걸어 두면 수집이 며칠 실패하는 것만으로
 * 데이터가 사라진다 — 갱신 주기 3일 + TTL 4일이라 유예가 하루뿐이었고, 한 조합이 한 번만 실패해도
 * 4일째 만료되어 다음 갱신(6일째)까지 약 이틀간 구멍이 났다. "수집 실패 시 저장을 건너뛰어
 * 기존 정책을 보존한다"는 리프레시 정책이 TTL 때문에 반쯤 무효화되던 셈이다.
 * 그래서 <b>만료 없이 저장하고, 갱신이 성공했을 때만 덮어쓴다</b>.
 *
 * <p>대신 만료가 사라져 "언제 받은 데이터인가"를 알 수 없게 되므로 <b>수집 시각</b>을
 * 페이로드에 함께 남긴다({@link SupportPolicyListPayload#getCollectedAt()}). stale 판단은
 * 만료가 아니라 이 값으로 한다.
 *
 * <p><b>이미 TTL 이 걸려 있는 운영 키</b>는 그대로 둬도 풀린다 — Redis 의 {@code SET} 은
 * {@code KEEPTTL} 을 주지 않으면 기존 TTL 을 버리므로, 다음 성공 저장에서 자동으로 영구 키가 된다
 * (Spring 의 {@code opsForValue().set(k, v)} 가 옵션 없는 {@code SET} 이다).
 * 다만 계속 수집에 실패하는 조합은 저장 자체가 일어나지 않아 옛 TTL 로 만료될 수 있다.
 *
 * <p>정본 저장소이므로 캐시처럼 조회 실패를 흡수하지 않는다("데이터 없음"과
 * "저장소 장애"를 구분해야 하므로 예외를 그대로 흘려보낸다 — redis-conventions §6.3).
 * 값이 없는 것은 실패가 아니라 "정책이 없다"는 정상 상태다.
 */
@Component
public class SupportPolicyRedisAdapter implements SupportPolicyRepository {

    private static final String KEY_PREFIX = "support:policy:";

    /** 수집 시각 표기. UTC ISO-8601(예: {@code 2026-08-19T06:12:31Z})이라 정렬·비교가 그대로 된다. */
    private static final DateTimeFormatter COLLECTED_AT_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, SupportPolicyListPayload> supportListRedisTemplate;
    private final Clock clock;

    @Autowired
    public SupportPolicyRedisAdapter(RedisTemplate<String, Object> redisTemplate,
                                     RedisTemplate<String, SupportPolicyListPayload> supportListRedisTemplate) {
        this(redisTemplate, supportListRedisTemplate, Clock.systemUTC());
    }

    /** 수집 시각을 고정해 검증하기 위한 생성자. */
    SupportPolicyRedisAdapter(RedisTemplate<String, Object> redisTemplate,
                              RedisTemplate<String, SupportPolicyListPayload> supportListRedisTemplate,
                              Clock clock) {
        this.redisTemplate = redisTemplate;
        this.supportListRedisTemplate = supportListRedisTemplate;
        this.clock = clock;
    }

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

    /**
     * 만료 없이 저장한다. 호출됐다는 것은 수집에 성공했다는 뜻이므로(실패한 조합은 유스케이스가
     * 저장을 건너뛴다) 여기서 덮어쓰는 것은 항상 "더 새로운 데이터"다.
     */
    @Override
    public void saveAll(SigunguCode code, SupportTag tag, List<SupportPolicy> policies) {
        SupportPolicyListPayload payload = new SupportPolicyListPayload();
        payload.setCollectedAt(COLLECTED_AT_FORMAT.format(clock.instant()));
        payload.setPolicies(policies.stream().map(this::toPayload).toList());

        supportListRedisTemplate.opsForValue().set(listKey(code, tag), payload);
        redisTemplate.opsForValue().set(countKey(code, tag), policies.size());
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
