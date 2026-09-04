package SDD.smash.domain.support.infrastructure.cache;

import SDD.smash.domain.support.domain.model.SupportScoreKey;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.CacheMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

/**
 * 지원정책 점수 캐시 어댑터. 임베디드 Redis 없이 {@code RedisTemplate} 을 목킹해
 * 키 조립·TTL·장애 흡수를 본다(redis-conventions §7.2).
 */
@ExtendWith(MockitoExtension.class)
class SupportScoreRedisAdapterTest {

    private static final SigunguCode JONGNO = SigunguCode.of("11110");

    /** 어댑터 상수와 같은 값. 표(redis-conventions §4.1)와 대조한다. */
    private static final String CACHE_NAME = "support:score";
    private static final String KEY_PREFIX = "support:score:";
    private static final Duration TTL = Duration.ofDays(4);

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private CacheMetrics cacheMetrics;

    private SupportScoreRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SupportScoreRedisAdapter(redisTemplate, cacheMetrics);
    }

    @Test
    @DisplayName("evictAll 은 support:score:0 부터 15 까지 16개 키를 열거해 삭제하고 keys() 를 쓰지 않는다")
    void evictAllEnumeratesSixteenKeys() {
        // given
        given(redisTemplate.delete(any(java.util.Collection.class))).willReturn(16L);

        // when
        adapter.evictAll();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        then(redisTemplate).should().delete(captor.capture());

        List<String> expected = IntStream.rangeClosed(0, 15)
                .mapToObj(i -> KEY_PREFIX + i)
                .toList();
        assertThat(captor.getValue()).containsExactlyElementsOf(expected);

        // keys() 금지 규칙(redis-conventions §6.2)
        then(redisTemplate).should(never()).keys(anyString());
    }

    @Test
    @DisplayName("put 은 putAll 직후 expire 를 한 쌍으로 호출한다(TTL 4일)")
    void putCallsPutAllThenExpire() {
        // given
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        SupportScoreKey key = SupportScoreKey.of(15);
        Map<SigunguCode, Score> scores = new LinkedHashMap<>();
        scores.put(JONGNO, Score.of(100));

        // when
        adapter.put(key, scores);

        // then — 순서까지 검증한다(putAll 뒤 expire 를 빠뜨리면 영구 키가 된다)
        InOrder order = inOrder(hashOperations, redisTemplate);
        order.verify(hashOperations).putAll(eq(KEY_PREFIX + "15"), eq(Map.of("11110", 100)));
        order.verify(redisTemplate).expire(KEY_PREFIX + "15", TTL);
    }

    @Test
    @DisplayName("빈 결과는 저장하지 않는다")
    void doesNotStoreEmptyResult() {
        // when
        adapter.put(SupportScoreKey.of(15), Map.of());

        // then — Redis 를 아예 건드리지 않는다
        then(redisTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("조회에 값이 있으면 도메인 맵으로 변환하고 hit 를 계측한다")
    void findReturnsDomainMapOnHit() {
        // given
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        Map<Object, Object> raw = new LinkedHashMap<>();
        raw.put("11110", 100);
        given(hashOperations.entries(KEY_PREFIX + "9")).willReturn(raw);

        // when
        Optional<Map<SigunguCode, Score>> result = adapter.find(SupportScoreKey.of(9));

        // then
        assertThat(result).contains(Map.of(JONGNO, Score.of(100)));
        then(cacheMetrics).should().hit(CACHE_NAME);
    }

    @Test
    @DisplayName("조회 결과가 비어 있으면 miss 로 흡수한다")
    void findReturnsEmptyOnMiss() {
        // given
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of());

        // when / then
        assertThat(adapter.find(SupportScoreKey.of(9))).isEmpty();
        then(cacheMetrics).should().miss(CACHE_NAME);
    }

    @Test
    @DisplayName("조회 중 예외는 Optional.empty() 로 흡수하고 error 를 계측한다")
    void findAbsorbsExceptionAsEmpty() {
        // given
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willThrow(new RuntimeException("redis down"));

        // when / then — 500 으로 흘리지 않는다
        assertThat(adapter.find(SupportScoreKey.of(9))).isEmpty();
        then(cacheMetrics).should().error(CACHE_NAME);
    }
}
