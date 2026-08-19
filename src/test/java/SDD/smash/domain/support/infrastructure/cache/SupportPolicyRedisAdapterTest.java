package SDD.smash.domain.support.infrastructure.cache;

import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 정본 저장소 어댑터. 임베디드 Redis 를 쓰지 않고 템플릿을 목킹해 <b>만료를 걸지 않는지</b>와
 * 키 조립·수집 시각 기록을 본다(redis-conventions §7.2).
 */
@ExtendWith(MockitoExtension.class)
class SupportPolicyRedisAdapterTest {

    private static final SigunguCode JONGNO = SigunguCode.of("11110");
    private static final String LIST_KEY = "support:policy:11110:HOUSING_SUPPORT";
    private static final String COUNT_KEY = "support:policy:11110:HOUSING_SUPPORT:count";
    private static final Instant COLLECTED_AT = Instant.parse("2026-08-19T06:12:31Z");

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private RedisTemplate<String, SupportPolicyListPayload> supportListRedisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private ValueOperations<String, SupportPolicyListPayload> listValueOperations;

    private SupportPolicyRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SupportPolicyRedisAdapter(redisTemplate, supportListRedisTemplate,
                Clock.fixed(COLLECTED_AT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("정본 저장에는 만료를 걸지 않는다")
    void savesWithoutExpiration() {
        // given
        given(supportListRedisTemplate.opsForValue()).willReturn(listValueOperations);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        adapter.saveAll(JONGNO, SupportTag.HOUSING_SUPPORT,
                List.of(new SupportPolicy("청년월세지원", "https://example.test", "주거지원")));

        // then — TTL 인자가 있는 오버로드도, 별도 expire 도 호출되지 않는다
        then(listValueOperations).should().set(eq(LIST_KEY), any(SupportPolicyListPayload.class));
        then(valueOperations).should().set(COUNT_KEY, 1);

        then(listValueOperations).should(never()).set(any(), any(), any(Duration.class));
        then(listValueOperations).should(never()).set(any(), any(), anyLong(), any(TimeUnit.class));
        then(valueOperations).should(never()).set(any(), any(), any(Duration.class));
        then(valueOperations).should(never()).set(any(), any(), anyLong(), any(TimeUnit.class));
        then(supportListRedisTemplate).should(never()).expire(any(), any(Duration.class));
        then(redisTemplate).should(never()).expire(any(), any(Duration.class));
    }

    @Test
    @DisplayName("만료가 없으므로 수집 시각을 값에 함께 남긴다")
    void stampsCollectionTimeOnSavedPayload() {
        // given
        given(supportListRedisTemplate.opsForValue()).willReturn(listValueOperations);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        ArgumentCaptor<SupportPolicyListPayload> captor = ArgumentCaptor.forClass(SupportPolicyListPayload.class);

        // when
        adapter.saveAll(JONGNO, SupportTag.HOUSING_SUPPORT,
                List.of(new SupportPolicy("청년월세지원", "https://example.test", "주거지원")));

        // then
        then(listValueOperations).should().set(eq(LIST_KEY), captor.capture());
        assertThat(captor.getValue().getCollectedAt()).isEqualTo("2026-08-19T06:12:31Z");
        assertThat(captor.getValue().getPolicies()).hasSize(1);
    }

    @Test
    @DisplayName("0건도 만료 없이 그대로 저장한다")
    void savesEmptyListWithoutExpiration() {
        // given
        given(supportListRedisTemplate.opsForValue()).willReturn(listValueOperations);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        adapter.saveAll(JONGNO, SupportTag.HOUSING_SUPPORT, List.of());

        // then
        then(valueOperations).should().set(COUNT_KEY, 0);
        then(listValueOperations).should(never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("수집 시각이 없는 옛 페이로드도 그대로 복원된다")
    void readsLegacyPayloadWithoutCollectionTime() {
        // given — TTL 시절에 저장된 값에는 collectedAt 이 없다
        SupportPolicyListPayload legacy = new SupportPolicyListPayload();
        legacy.setPolicies(List.of(new SupportPolicyPayload("청년월세지원", "https://example.test", "주거지원")));
        given(supportListRedisTemplate.opsForValue()).willReturn(listValueOperations);
        given(listValueOperations.get(LIST_KEY)).willReturn(legacy);

        // when
        List<SupportPolicy> policies = adapter.findBy(JONGNO, SupportTag.HOUSING_SUPPORT);

        // then
        assertThat(policies).containsExactly(
                new SupportPolicy("청년월세지원", "https://example.test", "주거지원"));
    }

    @Test
    @DisplayName("값이 없으면 장애가 아니라 빈 목록이다")
    void returnsEmptyListWhenNothingStored() {
        // given
        given(supportListRedisTemplate.opsForValue()).willReturn(listValueOperations);
        given(listValueOperations.get(LIST_KEY)).willReturn(null);

        // when / then
        assertThat(adapter.findBy(JONGNO, SupportTag.HOUSING_SUPPORT)).isEmpty();
    }
}
