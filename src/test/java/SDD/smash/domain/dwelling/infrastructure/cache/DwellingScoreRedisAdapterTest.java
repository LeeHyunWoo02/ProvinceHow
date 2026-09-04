package SDD.smash.domain.dwelling.infrastructure.cache;

import SDD.smash.domain.dwelling.domain.model.DwellingScoreKey;
import SDD.smash.domain.dwelling.domain.model.DwellingType;
import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 주거 점수 Redis 어댑터의 순수 로직(키 조립, TTL, 무효화 열거, 장애 흡수)을 검증한다.
 *
 * <p>임베디드 Redis 를 쓰지 않고 {@code RedisTemplate}/{@code HashOperations} 를 목킹한다.
 * 키 네임스페이스({@code dwelling:score:})와 TTL(30일)은 어댑터 상수를 그대로 따른다.
 */
@ExtendWith(MockitoExtension.class)
class DwellingScoreRedisAdapterTest {

    private static final Duration TTL = Duration.ofDays(30);
    private static final SigunguCode CODE = SigunguCode.of("11110");

    /** 계측은 대역이 아니라 실물을 쓴다. 어댑터 동작에는 관여하지 않는다. */
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final CacheMetrics cacheMetrics = new CacheMetrics(meterRegistry);

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    HashOperations<String, Object, Object> hashOps;

    @Captor
    ArgumentCaptor<Collection<String>> keysCaptor;

    private DwellingScoreRedisAdapter adapter() {
        return new DwellingScoreRedisAdapter(redisTemplate, cacheMetrics);
    }

    @Test
    @DisplayName("저장 시 키는 dwelling:score:{유형}:{보정예산}, putAll 직후 30일 TTL expire 가 쌍으로 호출된다")
    void putUsesHashKeyThenExpiresWithTtl() {
        // given
        given(redisTemplate.<Object, Object>opsForHash()).willReturn(hashOps);
        Map<SigunguCode, Score> scores = Map.of(CODE, Score.of(100));

        // when
        adapter().put(new DwellingScoreKey(DwellingType.MONTHLY, Money.of(60)), scores);

        // then - putAll 이 먼저, 그 직후 expire
        String expectedKey = "dwelling:score:MONTHLY:60";
        InOrder inOrder = inOrder(hashOps, redisTemplate);
        inOrder.verify(hashOps).putAll(eq(expectedKey), anyMap());
        inOrder.verify(redisTemplate).expire(eq(expectedKey), eq(TTL));
    }

    @Test
    @DisplayName("빈 결과는 저장하지 않는다(putAll/expire 미호출)")
    void doesNotStoreEmptyScores() {
        // when
        adapter().put(new DwellingScoreKey(DwellingType.MONTHLY, Money.of(60)), Map.of());

        // then
        verify(redisTemplate, never()).expire(any(), any());
        verify(hashOps, never()).putAll(any(), anyMap());
    }

    @Test
    @DisplayName("조회 히트 시 Hash 를 도메인 타입으로 복원해 돌려준다")
    void findReturnsDomainScoresOnHit() {
        // given - Redis 는 시군구코드(String) → 점수(Integer) 로 저장돼 있다
        given(redisTemplate.<Object, Object>opsForHash()).willReturn(hashOps);
        Map<Object, Object> stored = new LinkedHashMap<>();
        stored.put("11110", 87);
        given(hashOps.entries("dwelling:score:MONTHLY:60")).willReturn(stored);

        // when
        Optional<Map<SigunguCode, Score>> result =
                adapter().find(new DwellingScoreKey(DwellingType.MONTHLY, Money.of(60)));

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry(CODE, Score.of(87));
    }

    @Test
    @DisplayName("키가 비어 있으면 미스(Optional.empty)")
    void findMissWhenHashEmpty() {
        // given
        given(redisTemplate.<Object, Object>opsForHash()).willReturn(hashOps);
        given(hashOps.entries(any())).willReturn(new LinkedHashMap<>());

        // when & then
        assertThat(adapter().find(new DwellingScoreKey(DwellingType.JEONSE, Money.of(9_000)))).isEmpty();
    }

    @Test
    @DisplayName("조회 중 예외는 Optional.empty 로 흡수한다(500 으로 흘리지 않는다)")
    void findSwallowsExceptionAsMiss() {
        // given
        given(redisTemplate.<Object, Object>opsForHash()).willReturn(hashOps);
        given(hashOps.entries(any())).willThrow(new RuntimeException("redis down"));

        // when & then
        assertThat(adapter().find(new DwellingScoreKey(DwellingType.MONTHLY, Money.of(60)))).isEmpty();
    }

    @Test
    @DisplayName("무효화는 월세 10개 + 전세 7개 = 17개 키를 열거해 한 번에 삭제한다(KEYS 미사용)")
    void evictAllEnumeratesAllSeventeenKeys() {
        // when
        adapter().evictAll();

        // then
        verify(redisTemplate).delete(keysCaptor.capture());
        Collection<String> deleted = keysCaptor.getValue();

        assertThat(deleted).hasSize(17);
        // 월세: 20~110 을 10 단위로 = 10개
        assertThat(deleted).contains(
                "dwelling:score:MONTHLY:20", "dwelling:score:MONTHLY:60", "dwelling:score:MONTHLY:110");
        // 전세: 3000~21000 을 3000 단위로 = 7개
        assertThat(deleted).contains(
                "dwelling:score:JEONSE:3000", "dwelling:score:JEONSE:12000", "dwelling:score:JEONSE:21000");
        assertThat(deleted).filteredOn(k -> k.startsWith("dwelling:score:MONTHLY:")).hasSize(10);
        assertThat(deleted).filteredOn(k -> k.startsWith("dwelling:score:JEONSE:")).hasSize(7);
    }
}
