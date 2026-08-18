package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.RegionJobProfileView;
import SDD.smash.domain.job.domain.model.ExperienceLevel;
import SDD.smash.domain.job.domain.model.JobPostingSample;
import SDD.smash.domain.job.domain.model.RegionJobProfile;
import SDD.smash.domain.job.domain.port.RegionJobProfileCache;
import SDD.smash.domain.job.domain.port.RegionJobProfileProvider;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RegionJobProfileQueryServiceTest {

    @Mock RegionJobProfileProvider provider;
    @Mock RegionJobProfileCache cache;

    private final SigunguCode region = SigunguCode.of("11680");

    private RegionJobProfileQueryService service() {
        return new RegionJobProfileQueryService(provider, cache, 100, 5, Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("캐시가 있으면 공급자를 호출하지 않는다")
    void returnsCachedWithoutCallingProvider() {
        // given
        RegionJobProfile cached = new RegionJobProfile(region, 4000, 0.5, List.of(), 10, 8);
        given(cache.find(any())).willReturn(Optional.of(cached));

        // when
        RegionJobProfileView view = service().getProfile(region);

        // then
        assertThat(view.salaryMedianManwon()).isEqualTo(4000);
        assertThat(view.newcomerRatio()).isEqualTo(0.5);
        then(provider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시 미스면 표본을 받아 집계하고 캐시에 저장한다")
    void fetchesAggregatesAndCachesOnMiss() {
        // given
        given(cache.find(any())).willReturn(Optional.empty());
        given(provider.sample(any(), anyInt())).willReturn(Optional.of(List.of(
                new JobPostingSample(3000, 5000, ExperienceLevel.NEWCOMER, "IT"))));

        // when
        RegionJobProfileView view = service().getProfile(region);

        // then
        assertThat(view.sampleSize()).isEqualTo(1);
        assertThat(view.salaryMedianManwon()).isEqualTo(4000);
        then(cache).should().put(any());
    }

    @Test
    @DisplayName("공급자가 미시도(Optional.empty)면 빈 프로필이고 캐시에 저장하지 않는다")
    void doesNotCacheWhenProviderNotAttempted() {
        // given - access-key 미설정/역매핑 부재 시 공급자가 미시도(Optional.empty)를 준다
        given(cache.find(any())).willReturn(Optional.empty());
        given(provider.sample(any(), anyInt())).willReturn(Optional.empty());

        // when
        RegionJobProfileView view = service().getProfile(region);

        // then
        assertThat(view.sampleSize()).isZero();
        assertThat(view.salaryMedianManwon()).isNull();
        then(cache).should(Mockito.never()).put(any());
    }

    @Test
    @DisplayName("공급자가 실제 0건(Optional.of 빈 표본)을 주면 빈 프로필을 네거티브 캐싱한다")
    void negativeCachesRealEmptySamples() {
        // given - 실제 조회를 시도해 표본 0건
        given(cache.find(any())).willReturn(Optional.empty());
        given(provider.sample(any(), anyInt())).willReturn(Optional.of(List.of()));

        // when
        RegionJobProfileView view = service().getProfile(region);

        // then - 빈 프로필을 put(어댑터가 짧은 TTL 로 네거티브 캐싱)
        assertThat(view.sampleSize()).isZero();
        then(cache).should().put(any());
    }
}
