package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.application.NonCapitalJobRankingService;
import SDD.smash.domain.job.application.RegionJobStatisticsQueryService;
import SDD.smash.domain.job.application.dto.JobCategoryView;
import SDD.smash.domain.job.application.dto.NonCapitalRankView;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsTrendPoint;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsTrendPointSummary;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RegionJobStatisticsDetailServiceTest {

    private static final SigunguCode MOKPO = SigunguCode.of("46110");

    @Mock
    AddressQueryService addressQueryService;
    @Mock
    RegionJobStatisticsQueryService regionJobStatisticsQueryService;
    @Mock
    NonCapitalJobRankingService nonCapitalJobRankingService;
    @Mock
    JobQueryService jobQueryService;

    @InjectMocks
    RegionJobStatisticsDetailService service;

    @Test
    @DisplayName("직종 대분류를 코드 오름차순으로 정렬하고 직종명을 붙인다")
    void sortsByJobMajorCodeAndAttachesName() {
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(SigunguCode.of("11680")))
                .willReturn(List.of(
                        view("02", 30, 0),
                        view("01", 812, 1503)));
        given(jobQueryService.getAllTopCategories()).willReturn(List.of(
                new JobCategoryView(JobCode.of("01"), "경영·사무·금융·보험직"),
                new JobCategoryView(JobCode.of("02"), "연구직 및 공학 기술직")));
        given(nonCapitalJobRankingService.getRegionRank(any())).willReturn(Optional.empty());
        given(nonCapitalJobRankingService.getRegionRankByJob(any())).willReturn(Map.of());

        RegionJobStatisticsByJobSummary summary = service.byJob(SigunguCode.of("11680"), null);

        assertThat(summary.statisticsMonth()).isEqualTo("2026-07");
        assertThat(summary.items()).hasSize(2);
        assertThat(summary.items().get(0).jobMajorCode()).isEqualTo("01");
        assertThat(summary.items().get(0).jobMajorName()).isEqualTo("경영·사무·금융·보험직");
        assertThat(summary.items().get(0).validOpenings()).isEqualTo(812L);
        assertThat(summary.items().get(1).jobMajorCode()).isEqualTo("02");
        // 유효구직자수 0 이면 구인배수는 null 이다
        assertThat(summary.items().get(1).jobOpeningRatio()).isNull();
    }

    @Test
    @DisplayName("존재하지만 미적재면 기준월 없이 빈 목록을 준다")
    void returnsEmptyItemsWhenNotLoaded() {
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(any()))
                .willReturn(List.of());

        RegionJobStatisticsByJobSummary summary = service.byJob(SigunguCode.of("41110"), null);

        assertThat(summary.statisticsMonth()).isNull();
        assertThat(summary.items()).isEmpty();
        assertThat(summary.totalNonCapitalRank()).isNull();
        then(jobQueryService).should(never()).getAllTopCategories();
    }

    @Test
    @DisplayName("존재하지 않는 시군구면 ADDRESS_CODE_NOT_FOUND 를 던진다")
    void throwsWhenSigunguMissing() {
        willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"))
                .given(addressQueryService).checkSigunguExistsOrThrow(any());

        assertThatThrownBy(() -> service.byJob(SigunguCode.of("99999"), null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("합계 기준과 직종별 비수도권 백분위가 이름을 갈라 함께 실린다")
    void carriesTotalAndPerJobNonCapitalRanks() {
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(MOKPO)).willReturn(List.of(
                view("01", 300, 1_000),
                view("05", 100, 200)));
        given(jobQueryService.getAllTopCategories()).willReturn(List.of());
        given(nonCapitalJobRankingService.getRegionRank(MOKPO))
                .willReturn(Optional.of(new NonCapitalRankView(60, 40, 70, 173)));
        given(nonCapitalJobRankingService.getRegionRankByJob(MOKPO))
                .willReturn(Map.of("05", new NonCapitalRankView(95, 5, 9, 173)));

        RegionJobStatisticsByJobSummary summary = service.byJob(MOKPO, null);

        assertThat(summary.totalNonCapitalRank().percentile()).isEqualTo(60);
        // 백분위가 없는 직종은 null 이고 값이 있는 직종만 채워진다
        assertThat(summary.items().get(0).nonCapitalRank()).isNull();
        assertThat(summary.items().get(1).nonCapitalRank().topPercent()).isEqualTo(5);
    }

    @Test
    @DisplayName("midJobCode 를 주면 그 중분류가 속한 대분류 item 만 isSelected 다")
    void marksSelectedTopCategoryResolvedFromMidJobCode() {
        // given - 중분류 021 은 대분류 02 에 속한다
        given(jobQueryService.getTopCodeOfSubOrThrow(JobCode.of("021"))).willReturn(JobCode.of("02"));
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(MOKPO)).willReturn(List.of(
                view("01", 300, 1_000),
                view("02", 100, 200)));
        given(jobQueryService.getAllTopCategories()).willReturn(List.of());
        given(nonCapitalJobRankingService.getRegionRank(any())).willReturn(Optional.empty());
        given(nonCapitalJobRankingService.getRegionRankByJob(any())).willReturn(Map.of());

        RegionJobStatisticsByJobSummary summary = service.byJob(MOKPO, JobCode.of("021"));

        // then - 나머지는 생략이 아니라 명시적 false 다
        assertThat(summary.selectedJobMajorCode()).isEqualTo("02");
        assertThat(summary.items().get(0).isSelected()).isFalse();
        assertThat(summary.items().get(1).isSelected()).isTrue();
    }

    @Test
    @DisplayName("midJobCode 가 없으면 전 항목이 isSelected=false 다")
    void marksNothingSelectedWithoutMidJobCode() {
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(MOKPO))
                .willReturn(List.of(view("01", 300, 1_000)));
        given(jobQueryService.getAllTopCategories()).willReturn(List.of());
        given(nonCapitalJobRankingService.getRegionRank(any())).willReturn(Optional.empty());
        given(nonCapitalJobRankingService.getRegionRankByJob(any())).willReturn(Map.of());

        RegionJobStatisticsByJobSummary summary = service.byJob(MOKPO, null);

        assertThat(summary.selectedJobMajorCode()).isNull();
        assertThat(summary.items()).allSatisfy(item -> assertThat(item.isSelected()).isFalse());
        then(jobQueryService).should(never()).getTopCodeOfSubOrThrow(any());
    }

    @Test
    @DisplayName("없는 중분류 코드는 통계를 읽기 전에 JOB_CODE_NOT_FOUND 로 걸린다")
    void rejectsUnknownMidJobCodeBeforeReadingStatistics() {
        given(jobQueryService.getTopCodeOfSubOrThrow(JobCode.of("999")))
                .willThrow(new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다."));

        assertThatThrownBy(() -> service.byJob(MOKPO, JobCode.of("999")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.JOB_CODE_NOT_FOUND);

        then(regionJobStatisticsQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("통계가 미적재여도 고른 대분류 코드는 함께 내려간다")
    void keepsSelectedTopCodeEvenWhenStatisticsAreMissing() {
        given(jobQueryService.getTopCodeOfSubOrThrow(JobCode.of("021"))).willReturn(JobCode.of("02"));
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(MOKPO)).willReturn(List.of());

        RegionJobStatisticsByJobSummary summary = service.byJob(MOKPO, JobCode.of("021"));

        assertThat(summary.selectedJobMajorCode()).isEqualTo("02");
        assertThat(summary.items()).isEmpty();
    }

    @Test
    @DisplayName("추세는 존재 검증 후 job 폴딩 결과를 recommendation 요약으로 매핑한다")
    void trendMapsJobFoldingResult() {
        given(regionJobStatisticsQueryService.getRegionTrend(SigunguCode.of("11680"), 36))
                .willReturn(List.of(
                        new RegionJobStatisticsTrendPoint("2026-06", 100L, 500L, 0.2),
                        new RegionJobStatisticsTrendPoint("2026-07", 250L, 1400L, 250d / 1400d)));

        List<RegionJobStatisticsTrendPointSummary> points =
                service.trend(SigunguCode.of("11680"), 36);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).statisticsMonth()).isEqualTo("2026-06");
        assertThat(points.get(1).validOpenings()).isEqualTo(250L);
    }

    @Test
    @DisplayName("추세도 존재하지 않는 시군구면 ADDRESS_CODE_NOT_FOUND 를 던진다")
    void trendThrowsWhenSigunguMissing() {
        willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"))
                .given(addressQueryService).checkSigunguExistsOrThrow(any());

        assertThatThrownBy(() -> service.trend(SigunguCode.of("99999"), 36))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
    }

    private RegionJobStatisticsView view(String jobTopCode, long validOpenings, long validSeekers) {
        Double ratio = (validSeekers == 0L) ? null : (double) validOpenings / (double) validSeekers;
        return new RegionJobStatisticsView(
                SigunguCode.of("11680"), jobTopCode, "2026-07",
                0, 0, 0, validOpenings, validSeekers, ratio);
    }
}
