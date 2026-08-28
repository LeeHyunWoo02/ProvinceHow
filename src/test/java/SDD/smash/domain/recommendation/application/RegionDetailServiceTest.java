package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.address.application.PopulationQueryService;
import SDD.smash.domain.address.application.dto.RegionCodeView;
import SDD.smash.domain.dwelling.application.DwellingQueryService;
import SDD.smash.domain.infra.application.InfraQueryService;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.application.JobVacancyQueryService;
import SDD.smash.domain.job.application.RegionJobProfileQueryService;
import SDD.smash.domain.job.application.RegionJobStatisticsQueryService;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;
import SDD.smash.domain.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsSummary;
import SDD.smash.domain.support.application.SupportQueryService;
import SDD.smash.global.domain.model.SidoCode;
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
import static org.mockito.Mockito.never;

/**
 * 고용행정통계가 지역 상세에 실리는 경로를 확인한다. 다른 컨텍스트 Service 는 전부 목이고
 * 스텁하지 않은 것은 Mockito 기본값(빈 목록/null)이 그대로 쓰인다.
 */
@ExtendWith(MockitoExtension.class)
class RegionDetailServiceTest {

    private static final SigunguCode GANGNAM = SigunguCode.of("11680");

    @Mock AddressQueryService addressQueryService;
    @Mock PopulationQueryService populationQueryService;
    @Mock JobQueryService jobQueryService;
    @Mock JobVacancyQueryService jobVacancyQueryService;
    @Mock RegionJobProfileQueryService regionJobProfileQueryService;
    @Mock RegionJobStatisticsQueryService regionJobStatisticsQueryService;
    @Mock SupportQueryService supportQueryService;
    @Mock DwellingQueryService dwellingQueryService;
    @Mock InfraQueryService infraQueryService;

    @InjectMocks RegionDetailService regionDetailService;

    @Test
    @DisplayName("고용행정통계가 있으면 기준월과 함께 시군구 합계로 접혀 실린다")
    void carriesJobStatisticsFoldedIntoSigunguTotals() {
        // given - 그 시군구의 직종 대분류 2행
        givenRegionCode();
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(GANGNAM)).willReturn(List.of(
                view(GANGNAM, "01", 100L, 400L, 30L, 300L, 1_000L),
                view(GANGNAM, "02", 50L, 200L, 20L, 200L, 1_000L)));

        // when
        RegionDetailInfo detail = regionDetailService.details(GANGNAM, null);

        // then
        RegionJobStatisticsSummary statistics = detail.getJobStatistics();
        assertThat(statistics).isNotNull();
        assertThat(statistics.statisticsMonth()).isEqualTo("2026-07");
        assertThat(statistics.validOpenings()).isEqualTo(500L);
        assertThat(statistics.validSeekers()).isEqualTo(2_000L);
        assertThat(statistics.jobOpenings()).isEqualTo(150L);
        assertThat(statistics.jobSeekers()).isEqualTo(600L);
        assertThat(statistics.placements()).isEqualTo(50L);
        assertThat(statistics.jobOpeningRatio()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("유효구직자수가 0 이면 구인배수는 0 이 아니라 null 이다")
    void leavesJobOpeningRatioNullWhenNoSeekers() {
        // given
        givenRegionCode();
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(GANGNAM))
                .willReturn(List.of(view(GANGNAM, "01", 10L, 0L, 0L, 40L, 0L)));

        // when
        RegionDetailInfo detail = regionDetailService.details(GANGNAM, null);

        // then
        assertThat(detail.getJobStatistics().validOpenings()).isEqualTo(40L);
        assertThat(detail.getJobStatistics().jobOpeningRatio()).isNull();
    }

    @Test
    @DisplayName("통계가 아직 적재되지 않았으면 jobStatistics 가 null 이다")
    void leavesJobStatisticsNullWhenNotLoaded() {
        // given
        givenRegionCode();
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(GANGNAM)).willReturn(List.of());

        // when
        RegionDetailInfo detail = regionDetailService.details(GANGNAM, null);

        // then - 예외도 0 도 아니고 필드만 빈다
        assertThat(detail.getJobStatistics()).isNull();
        assertThat(detail.getSigunguCode()).isEqualTo("11680");
    }

    @Test
    @DisplayName("요청한 시군구만 조회하고 전국 통계는 읽지 않는다")
    void queriesOnlyRequestedRegionInsteadOfNationwide() {
        // given
        givenRegionCode();
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(GANGNAM)).willReturn(List.of());

        // when
        regionDetailService.details(GANGNAM, null);

        // then - 전국 3,432행을 받아 메모리에서 거르지 않는다
        then(regionJobStatisticsQueryService).should().getLatestStatisticsOfRegion(GANGNAM);
        then(regionJobStatisticsQueryService).should(never()).getLatestStatistics(null);
    }

    @Test
    @DisplayName("채용공고 목록은 빈 목록으로 유지된다(직행 OpenAPI 대기)")
    void keepsJobVacanciesAsEmptyList() {
        // given
        givenRegionCode();
        given(regionJobStatisticsQueryService.getLatestStatisticsOfRegion(GANGNAM)).willReturn(List.of());

        // when
        RegionDetailInfo detail = regionDetailService.details(GANGNAM, null);

        // then
        assertThat(detail.getJobVacancies()).isEmpty();
    }

    private void givenRegionCode() {
        given(addressQueryService.getRegionCode(GANGNAM)).willReturn(Optional.of(
                new RegionCodeView(SidoCode.of("11"), "서울특별시", GANGNAM, "강남구")));
    }

    private RegionJobStatisticsView view(SigunguCode sigunguCode, String jobTopCode,
                                         long jobOpenings, long jobSeekers, long placements,
                                         long validOpenings, long validSeekers) {
        Double ratio = validSeekers == 0L ? null : (double) validOpenings / (double) validSeekers;
        return new RegionJobStatisticsView(sigunguCode, jobTopCode, "2026-07",
                jobOpenings, jobSeekers, placements, validOpenings, validSeekers, ratio);
    }
}
