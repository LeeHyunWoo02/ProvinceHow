package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.application.RegionJobStatisticsQueryService;
import SDD.smash.domain.job.application.dto.JobCategoryView;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class RegionJobStatisticsDetailServiceTest {

    @Mock
    AddressQueryService addressQueryService;
    @Mock
    RegionJobStatisticsQueryService regionJobStatisticsQueryService;
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

        RegionJobStatisticsByJobSummary summary = service.byJob(SigunguCode.of("11680"));

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
        given(jobQueryService.getAllTopCategories()).willReturn(List.of());

        RegionJobStatisticsByJobSummary summary = service.byJob(SigunguCode.of("41110"));

        assertThat(summary.statisticsMonth()).isNull();
        assertThat(summary.items()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 시군구면 ADDRESS_CODE_NOT_FOUND 를 던진다")
    void throwsWhenSigunguMissing() {
        willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"))
                .given(addressQueryService).checkSigunguExistsOrThrow(any());

        assertThatThrownBy(() -> service.byJob(SigunguCode.of("99999")))
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
