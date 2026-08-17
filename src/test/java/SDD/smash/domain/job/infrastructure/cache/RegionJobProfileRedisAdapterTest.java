package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.domain.job.domain.model.IndustryShare;
import SDD.smash.domain.job.domain.model.RegionJobProfile;
import SDD.smash.global.domain.model.SigunguCode;
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
class RegionJobProfileRedisAdapterTest {

    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(30);
    private static final Duration POSITIVE_TTL = Duration.ofHours(12);

    @Mock RedisTemplate<String, RegionJobProfilePayload> template;
    @Mock ValueOperations<String, RegionJobProfilePayload> valueOps;

    private RegionJobProfileRedisAdapter adapter;
    private final SigunguCode region = SigunguCode.of("11680");

    @BeforeEach
    void setUp() {
        lenient().when(template.opsForValue()).thenReturn(valueOps);
        adapter = new RegionJobProfileRedisAdapter(template, NEGATIVE_TTL);
    }

    @Test
    @DisplayName("키는 job:profile:{시군구}, 정상 프로필은 12시간 TTL 로 저장한다")
    void putPositiveUsesRegionKeyAndPositiveTtl() {
        RegionJobProfile profile = new RegionJobProfile(
                region, 4000, 0.5, List.of(new IndustryShare("IT", 3)), 10, 8);

        adapter.put(profile);

        then(valueOps).should().set(eq("job:profile:11680"), any(RegionJobProfilePayload.class), eq(POSITIVE_TTL));
    }

    @Test
    @DisplayName("빈 프로필(표본 0건)은 네거티브 TTL(짧게)로 저장한다")
    void putEmptyUsesNegativeTtl() {
        adapter.put(RegionJobProfile.empty(region));

        then(valueOps).should().set(eq("job:profile:11680"), any(RegionJobProfilePayload.class), eq(NEGATIVE_TTL));
    }

    @Test
    @DisplayName("키가 없으면 미스(Optional.empty)")
    void findMissWhenKeyAbsent() {
        given(valueOps.get("job:profile:11680")).willReturn(null);

        assertThat(adapter.find(region)).isEmpty();
    }

    @Test
    @DisplayName("빈 프로필이 캐시돼 있으면 네거티브 히트(Optional.of 빈 프로필)")
    void findNegativeHit() {
        RegionJobProfilePayload payload = new RegionJobProfilePayload(null, null, List.of(), 0, 0);
        given(valueOps.get("job:profile:11680")).willReturn(payload);

        Optional<RegionJobProfile> result = adapter.find(region);

        assertThat(result).isPresent();
        assertThat(result.get().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("정상 프로필이 캐시돼 있으면 도메인으로 복원해 돌려준다")
    void findPositiveHit() {
        RegionJobProfilePayload payload = new RegionJobProfilePayload(
                4000, 0.5,
                List.of(new RegionJobProfilePayload.IndustrySharePayload("IT", 3)), 10, 8);
        given(valueOps.get("job:profile:11680")).willReturn(payload);

        Optional<RegionJobProfile> result = adapter.find(region);

        assertThat(result).isPresent();
        assertThat(result.get().salaryMedianManwon()).isEqualTo(4000);
        assertThat(result.get().topIndustries()).extracting(IndustryShare::name).containsExactly("IT");
    }

    @Test
    @DisplayName("조회 중 예외는 미스로 흡수한다")
    void findSwallowsException() {
        given(valueOps.get(any())).willThrow(new RuntimeException("redis down"));

        assertThat(adapter.find(region)).isEmpty();
    }
}
