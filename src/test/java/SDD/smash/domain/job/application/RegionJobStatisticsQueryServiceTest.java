package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.RegionJobStatisticsTrendPoint;
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

    @Test
    @DisplayName("시군구 전 직종 조회는 최신월을 찾아 그 시군구 조건으로만 읽는다")
    void readsEveryJobCodeOfSingleRegionOnLatestMonth() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.of(LATEST));
        given(regionJobStatisticsRepository.findAllByMonthAndSigunguCode(LATEST, SigunguCode.of("11110")))
                .willReturn(List.of(
                        statistics("11110", "01", 200, 1_000),
                        statistics("11110", "02", 50, 400)));

        List<RegionJobStatisticsView> views = service.getLatestStatisticsOfRegion(SigunguCode.of("11110"));

        assertThat(views).hasSize(2);
        assertThat(views).allSatisfy(view -> assertThat(view.statisticsMonth()).isEqualTo("2026-07"));
        // 전국을 읽어 메모리에서 거르지 않는다
        then(regionJobStatisticsRepository).should(org.mockito.Mockito.never())
                .findAllByMonth(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("적재 전이면 시군구 전 직종 조회도 저장소를 건드리지 않고 빈 결과를 준다")
    void returnsEmptyRegionStatisticsWhenNothingLoaded() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.empty());

        assertThat(service.getLatestStatisticsOfRegion(SigunguCode.of("11110"))).isEmpty();
        then(regionJobStatisticsRepository).should(org.mockito.Mockito.never())
                .findAllByMonthAndSigunguCode(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("추세는 월별로 직종 합계를 접고 월 오름차순으로 방출한다")
    void foldsTrendByMonthInAscendingOrder() {
        given(regionJobStatisticsRepository.findAllBySigunguCode(SigunguCode.of("11680")))
                .willReturn(List.of(
                        // 2026-07: 두 직종 합산 → 유효구인 250, 유효구직 1400
                        monthly("11680", "01", "2026-07", 200, 1_000),
                        monthly("11680", "02", "2026-07", 50, 400),
                        // 2026-06: 한 직종
                        monthly("11680", "01", "2026-06", 100, 500)));

        List<RegionJobStatisticsTrendPoint> points =
                service.getRegionTrend(SigunguCode.of("11680"), 36);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).statisticsMonth()).isEqualTo("2026-06");
        assertThat(points.get(1).statisticsMonth()).isEqualTo("2026-07");
        assertThat(points.get(1).validOpenings()).isEqualTo(250L);
        assertThat(points.get(1).validSeekers()).isEqualTo(1_400L);
        assertThat(points.get(1).jobOpeningRatio()).isEqualTo(250d / 1_400d);
    }

    @Test
    @DisplayName("최근 N개월만 남기고 앞선 월은 잘라낸다")
    void keepsOnlyRecentMonths() {
        given(regionJobStatisticsRepository.findAllBySigunguCode(SigunguCode.of("11680")))
                .willReturn(List.of(
                        monthly("11680", "01", "2026-05", 10, 10),
                        monthly("11680", "01", "2026-06", 20, 20),
                        monthly("11680", "01", "2026-07", 30, 30)));

        List<RegionJobStatisticsTrendPoint> points =
                service.getRegionTrend(SigunguCode.of("11680"), 2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).statisticsMonth()).isEqualTo("2026-06");
        assertThat(points.get(1).statisticsMonth()).isEqualTo("2026-07");
    }

    @Test
    @DisplayName("그 달 유효구직자 합계가 0 이면 구인배수는 null 이다")
    void trendRatioIsNullWhenSeekersZero() {
        given(regionJobStatisticsRepository.findAllBySigunguCode(SigunguCode.of("11680")))
                .willReturn(List.of(monthly("11680", "01", "2026-07", 40, 0)));

        List<RegionJobStatisticsTrendPoint> points =
                service.getRegionTrend(SigunguCode.of("11680"), 36);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).jobOpeningRatio()).isNull();
    }

    @Test
    @DisplayName("적재된 월이 없으면 빈 추세를 준다")
    void trendIsEmptyWhenNothingLoaded() {
        given(regionJobStatisticsRepository.findAllBySigunguCode(SigunguCode.of("11680")))
                .willReturn(List.of());

        assertThat(service.getRegionTrend(SigunguCode.of("11680"), 36)).isEmpty();
    }

    private RegionJobStatistics statistics(String sigunguCode, String jobCode,
                                           long validOpenings, long validSeekers) {
        return RegionJobStatistics.reconstitute(
                new RegionJobStatisticsKey(SigunguCode.of(sigunguCode), JobCode.of(jobCode), LATEST),
                0, 0, 0, validOpenings, validSeekers);
    }

    private RegionJobStatistics monthly(String sigunguCode, String jobCode, String month,
                                        long validOpenings, long validSeekers) {
        return RegionJobStatistics.reconstitute(
                new RegionJobStatisticsKey(SigunguCode.of(sigunguCode), JobCode.of(jobCode),
                        StatisticsMonth.of(month)),
                0, 0, 0, validOpenings, validSeekers);
    }
}
