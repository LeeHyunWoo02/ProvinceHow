package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobCount;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.domain.port.RegionJobStatisticsRepository;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RegionJobStatisticsQueryServiceTest {

    private static final StatisticsMonth LATEST = StatisticsMonth.of("2026-07");

    @Mock
    RegionJobStatisticsRepository regionJobStatisticsRepository;

    @InjectMocks
    RegionJobStatisticsQueryService service;

    @Test
    @DisplayName("최신월 통계에는 기준월이 함께 담긴다")
    void includesBaseMonthInEveryView() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.of(LATEST));
        given(regionJobStatisticsRepository.findAllByMonthAndJobCode(LATEST, JobCode.of("01")))
                .willReturn(List.of(statistics("11110", "01", 200, 1_000)));

        List<RegionJobStatisticsView> views = service.getLatestStatistics(JobCode.of("01"));

        assertThat(views).hasSize(1);
        assertThat(views.get(0).statisticsMonth()).isEqualTo("2026-07");
        assertThat(views.get(0).jobOpeningRatio()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("직종을 지정하지 않으면 최신월 전체를 읽는다")
    void readsEveryJobCodeWhenNotSpecified() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.of(LATEST));
        given(regionJobStatisticsRepository.findAllByMonth(LATEST))
                .willReturn(List.of(statistics("11110", "01", 200, 1_000)));

        assertThat(service.getLatestStatistics(null)).hasSize(1);
        then(regionJobStatisticsRepository).should(org.mockito.Mockito.never())
                .findAllByMonthAndJobCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("적재된 데이터가 없으면 조회하지 않고 빈 결과를 준다")
    void returnsEmptyWhenNothingLoaded() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.empty());

        assertThat(service.getLatestStatistics(null)).isEmpty();
        assertThat(service.getLatestMonth()).isEmpty();
    }

    @Test
    @DisplayName("점수 입력용 투영은 최신월 유효구인인원을 시군구 단위로 합산한다")
    void projectsValidOpeningsIntoRegionJobCount() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.of(LATEST));
        given(regionJobStatisticsRepository.findAllByMonth(LATEST)).willReturn(List.of(
                statistics("11110", "01", 200, 1_000),
                statistics("11110", "02", 50, 400),
                statistics("11140", "01", 30, 100)));

        List<RegionJobCount> counts = service.getLatestValidOpenings(null);

        assertThat(counts).containsExactly(
                new RegionJobCount(SigunguCode.of("11110"), 250L),
                new RegionJobCount(SigunguCode.of("11140"), 30L));
    }

    @Test
    @DisplayName("시군구 단건은 최신월 기준으로 찾는다")
    void findsSingleRegionOnLatestMonth() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.of(LATEST));
        given(regionJobStatisticsRepository.findOne(new RegionJobStatisticsKey(
                SigunguCode.of("11110"), JobCode.of("01"), LATEST)))
                .willReturn(Optional.of(statistics("11110", "01", 200, 1_000)));

        Optional<RegionJobStatisticsView> view =
                service.getLatestStatisticsOf(SigunguCode.of("11110"), JobCode.of("01"));

        assertThat(view).isPresent();
        assertThat(view.get().validOpenings()).isEqualTo(200L);
    }

    private RegionJobStatistics statistics(String sigunguCode, String jobCode,
                                           long validOpenings, long validSeekers) {
        return RegionJobStatistics.reconstitute(
                new RegionJobStatisticsKey(SigunguCode.of(sigunguCode), JobCode.of(jobCode), LATEST),
                0, 0, 0, validOpenings, validSeekers);
    }
}
