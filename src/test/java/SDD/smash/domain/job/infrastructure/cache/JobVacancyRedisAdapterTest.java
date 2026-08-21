package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class JobVacancyRedisAdapterTest {

    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(30);
    private static final Duration POSITIVE_TTL = Duration.ofHours(6);

    /** 계측은 대역이 아니라 실물을 쓴다. 카운터 값을 그대로 단정할 수 있다. */
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final CacheMetrics cacheMetrics = new CacheMetrics(meterRegistry);

    @Mock RedisTemplate<String, JobVacancyListPayload> template;
    @Mock ValueOperations<String, JobVacancyListPayload> valueOps;

    private JobVacancyRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        // opsForValue() 는 호출마다 새 객체를 반환하므로 반드시 스텁을 건다.
        lenient().when(template.opsForValue()).thenReturn(valueOps);
        adapter = new JobVacancyRedisAdapter(template, NEGATIVE_TTL, cacheMetrics);
    }

    private JobVacancy vacancy() {
        return new JobVacancy(JobPostingId.of("1"), "백엔드", "회사", "url", "지역", "직종",
                null, null, null, null, true, null, null);
    }

    @Test
    @DisplayName("키는 job:vacancy:{시군구}, 정상 결과는 6시간 TTL 로 저장한다")
    void putPositiveUsesRegionKeyAndPositiveTtl() {
        // when
        adapter.put(SigunguCode.of("11680"), List.of(vacancy()));

        // then
        then(valueOps).should().set(eq("job:vacancy:11680"), any(JobVacancyListPayload.class), eq(POSITIVE_TTL));
    }

    @Test
    @DisplayName("빈 결과는 네거티브 TTL(짧게)로 저장한다")
    void putEmptyUsesNegativeTtl() {
        // when
        adapter.put(SigunguCode.of("11680"), List.of());

        // then
        then(valueOps).should().set(eq("job:vacancy:11680"), any(JobVacancyListPayload.class), eq(NEGATIVE_TTL));
    }

    @Test
    @DisplayName("키가 없으면 미스(Optional.empty)")
    void findMissWhenKeyAbsent() {
        given(valueOps.get("job:vacancy:11680")).willReturn(null);

        assertThat(adapter.find(SigunguCode.of("11680"))).isEmpty();
    }

    @Test
    @DisplayName("빈 목록이 캐시돼 있으면 네거티브 히트(Optional.of 빈 목록)")
    void findNegativeHitWhenEmptyListCached() {
        JobVacancyListPayload payload = new JobVacancyListPayload();
        payload.setVacancies(List.of());
        given(valueOps.get("job:vacancy:11680")).willReturn(payload);

        Optional<List<JobVacancy>> result = adapter.find(SigunguCode.of("11680"));

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("정상 목록이 캐시돼 있으면 도메인으로 복원해 돌려준다")
    void findPositiveHit() {
        JobVacancyListPayload payload = new JobVacancyListPayload();
        payload.setVacancies(List.of(new JobVacancyPayload(
                "1", "백엔드", "회사", "url", "지역", "직종",
                null, null, null, null, true, null, null)));
        given(valueOps.get("job:vacancy:11680")).willReturn(payload);

        Optional<List<JobVacancy>> result = adapter.find(SigunguCode.of("11680"));

        assertThat(result).isPresent();
        assertThat(result.get()).extracting(JobVacancy::title).containsExactly("백엔드");
    }

    @Test
    @DisplayName("조회 중 예외는 미스로 흡수한다")
    void findSwallowsException() {
        given(valueOps.get(any())).willThrow(new RuntimeException("redis down"));

        assertThat(adapter.find(SigunguCode.of("11680"))).isEmpty();
    }
}
