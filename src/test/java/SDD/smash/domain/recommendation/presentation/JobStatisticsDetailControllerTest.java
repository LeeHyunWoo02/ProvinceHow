package SDD.smash.domain.recommendation.presentation;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.RegionJobStatisticsDetailService;
import SDD.smash.domain.recommendation.application.dto.NonCapitalRankSummary;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobItem;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsTrendPointSummary;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobStatisticsDetailController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobStatisticsDetailControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegionJobStatisticsDetailService regionJobStatisticsDetailService;

    @Test
    @DisplayName("직종 분해 응답이 기준월과 코드 오름차순 items 로 실린다")
    void byJobResponseCarriesSortedItems() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), isNull())).willReturn(
                new RegionJobStatisticsByJobSummary("2026-07", null, null, List.of(
                        new RegionJobStatisticsByJobItem("01", "경영·사무·금융·보험직", false,
                                812L, 1503L, 0.5403, null, 640L, 401L, 132L),
                        new RegionJobStatisticsByJobItem("02", "연구직 및 공학 기술직", false,
                                40L, 0L, null, null, 10L, 0L, 0L))));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statisticsMonth").value("2026-07"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].jobMajorCode").value("01"))
                .andExpect(jsonPath("$.items[0].jobMajorName").value("경영·사무·금융·보험직"))
                .andExpect(jsonPath("$.items[0].isSelected").value(false))
                .andExpect(jsonPath("$.items[0].validOpenings").value(812))
                .andExpect(jsonPath("$.items[0].validSeekers").value(1503))
                .andExpect(jsonPath("$.items[0].jobOpeningRatio").value(0.5403))
                .andExpect(jsonPath("$.items[0].jobOpenings").value(640))
                .andExpect(jsonPath("$.items[0].jobSeekers").value(401))
                .andExpect(jsonPath("$.items[0].placements").value(132))
                .andExpect(jsonPath("$.items[1].jobMajorCode").value("02"))
                // 유효구직자수 0 이면 구인배수는 null 로 실린다
                .andExpect(content().string(containsString("\"jobOpeningRatio\":null")));
    }

    @Test
    @DisplayName("합계 기준과 직종별 비수도권 백분위가 서로 다른 이름으로 실린다")
    void byJobResponseSplitsTotalAndPerJobRank() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), isNull())).willReturn(
                new RegionJobStatisticsByJobSummary("2026-07", null,
                        new NonCapitalRankSummary(60, 40, 70, 173), List.of(
                        new RegionJobStatisticsByJobItem("01", "경영·사무·금융·보험직", false,
                                300L, 1_000L, 0.3, new NonCapitalRankSummary(55, 45, 78, 173),
                                120L, 400L, 90L),
                        new RegionJobStatisticsByJobItem("05", "예술·디자인·방송·스포츠직", false,
                                100L, 200L, 0.5, new NonCapitalRankSummary(95, 5, 9, 173),
                                40L, 80L, 20L))));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "46110"))
                .andExpect(status().isOk())
                // 13종 합계 기준 순위. item 의 nonCapitalRank 와 다른 값이라 이름을 갈라 둔다
                .andExpect(jsonPath("$.totalNonCapitalRank.percentile").value(60))
                .andExpect(jsonPath("$.totalNonCapitalRank.topPercent").value(40))
                .andExpect(jsonPath("$.totalNonCapitalRank.rank").value(70))
                .andExpect(jsonPath("$.totalNonCapitalRank.total").value(173))
                // 직종별 백분위는 항목마다 다르다
                .andExpect(jsonPath("$.items[0].nonCapitalRank.percentile").value(55))
                .andExpect(jsonPath("$.items[1].nonCapitalRank.percentile").value(95))
                .andExpect(jsonPath("$.items[1].nonCapitalRank.topPercent").value(5));
    }

    @Test
    @DisplayName("수도권 지역은 백분위 없이 통계만 실린다")
    void byJobResponseOmitsRankForCapitalAreaRegion() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), isNull())).willReturn(
                new RegionJobStatisticsByJobSummary("2026-07", null, null, List.of(
                        new RegionJobStatisticsByJobItem("01", "경영·사무·금융·보험직", false,
                                800L, 2_000L, 0.4, null, 300L, 900L, 200L))));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].validOpenings").value(800))
                .andExpect(jsonPath("$.totalNonCapitalRank").doesNotExist())
                .andExpect(jsonPath("$.items[0].nonCapitalRank").doesNotExist());
    }

    @Test
    @DisplayName("midJobCode 를 주면 그 대분류 item 만 isSelected=true 이고 나머지는 명시적 false 다")
    void byJobMarksSelectedTopCategoryWhenMidJobCodeGiven() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), eq(JobCode.of("021")))).willReturn(
                new RegionJobStatisticsByJobSummary("2026-07", "02", null, List.of(
                        new RegionJobStatisticsByJobItem("01", "경영·사무·금융·보험직", false,
                                300L, 1_000L, 0.3, null, 120L, 400L, 90L),
                        new RegionJobStatisticsByJobItem("02", "연구직 및 공학 기술직", true,
                                200L, 100L, 2.0, new NonCapitalRankSummary(98, 2, 3, 173),
                                80L, 40L, 30L))));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob")
                        .param("sigunguCode", "46110")
                        .param("midJobCode", "021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedJobTopCode").value("02"))
                .andExpect(jsonPath("$.items[0].isSelected").value(false))
                .andExpect(jsonPath("$.items[1].isSelected").value(true))
                .andExpect(jsonPath("$.items[1].nonCapitalRank.topPercent").value(2));
    }

    @Test
    @DisplayName("빈 midJobCode 는 지정하지 않은 것으로 본다")
    void byJobTreatsBlankMidJobCodeAsAbsent() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), isNull()))
                .willReturn(RegionJobStatisticsByJobSummary.empty(null));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob")
                        .param("sigunguCode", "46110")
                        .param("midJobCode", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"selectedJobTopCode\":null")));
    }

    @Test
    @DisplayName("없는 중분류 코드는 /api/detail 과 같이 404 와 JOB_CODE_NOT_FOUND 다")
    void byJobRejectsUnknownMidJobCode() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), any()))
                .willThrow(new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다."));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob")
                        .param("sigunguCode", "46110")
                        .param("midJobCode", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_CODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("미적재/시 레벨이면 200 과 함께 statisticsMonth=null, items=[] 로 내려간다")
    void byJobResponseIsEmptyWhenNotLoaded() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), isNull()))
                .willReturn(RegionJobStatisticsByJobSummary.empty(null));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "41110"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(content().string(containsString("\"statisticsMonth\":null")))
                .andExpect(content().string(containsString("\"totalNonCapitalRank\":null")));
    }

    @Test
    @DisplayName("통계가 미적재여도 고른 대분류 코드는 함께 내려간다")
    void byJobKeepsSelectedTopCodeWhenNotLoaded() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), eq(JobCode.of("021"))))
                .willReturn(RegionJobStatisticsByJobSummary.empty("02"));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob")
                        .param("sigunguCode", "41110")
                        .param("midJobCode", "021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedJobTopCode").value("02"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 시군구면 404 와 ADDRESS_CODE_NOT_FOUND 를 반환한다")
    void byJobReturns404WhenSigunguMissing() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any(), isNull()))
                .willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_CODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("추세 응답이 sigunguCode 와 월 오름차순 points 로 실린다")
    void trendResponseCarriesAscendingPoints() throws Exception {
        given(regionJobStatisticsDetailService.trend(any(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(List.of(
                        new RegionJobStatisticsTrendPointSummary("2023-08", 3980L, 9120L, 0.4364),
                        new RegionJobStatisticsTrendPointSummary("2026-07", 40L, 0L, null)));

        mockMvc.perform(get("/api/detail/jobStatistics/trend").param("sigunguCode", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sigunguCode").value("11680"))
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].statisticsMonth").value("2023-08"))
                .andExpect(jsonPath("$.points[0].validOpenings").value(3980))
                .andExpect(jsonPath("$.points[0].validSeekers").value(9120))
                .andExpect(jsonPath("$.points[0].jobOpeningRatio").value(0.4364))
                // 세 지표만 싣는다 — jobOpenings 등은 point 에 없다
                .andExpect(jsonPath("$.points[0].jobOpenings").doesNotExist())
                // 유효구직자 0 인 달은 구인배수 null
                .andExpect(content().string(containsString("\"jobOpeningRatio\":null")));
    }

    @Test
    @DisplayName("추세가 미적재면 200 과 함께 points 가 빈 배열로 내려간다")
    void trendResponseIsEmptyWhenNotLoaded() throws Exception {
        given(regionJobStatisticsDetailService.trend(any(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/detail/jobStatistics/trend").param("sigunguCode", "41110"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sigunguCode").value("41110"))
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    @DisplayName("months 가 범위를 벗어나면 400 과 BIND_FAILED 를 반환한다")
    void trendRejectsOutOfRangeMonths() throws Exception {
        mockMvc.perform(get("/api/detail/jobStatistics/trend")
                        .param("sigunguCode", "11680")
                        .param("months", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIND_FAILED"));
    }
}
