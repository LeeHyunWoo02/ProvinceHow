package SDD.smash.domain.recommendation.presentation;

import SDD.smash.domain.recommendation.application.RegionJobStatisticsDetailService;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobItem;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
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
        given(regionJobStatisticsDetailService.byJob(any())).willReturn(
                new RegionJobStatisticsByJobSummary("2026-07", List.of(
                        new RegionJobStatisticsByJobItem("01", "경영·사무·금융·보험직",
                                812L, 1503L, 0.5403, 640L, 401L, 132L),
                        new RegionJobStatisticsByJobItem("02", "연구직 및 공학 기술직",
                                40L, 0L, null, 10L, 0L, 0L))));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statisticsMonth").value("2026-07"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].jobMajorCode").value("01"))
                .andExpect(jsonPath("$.items[0].jobMajorName").value("경영·사무·금융·보험직"))
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
    @DisplayName("미적재/시 레벨이면 200 과 함께 statisticsMonth=null, items=[] 로 내려간다")
    void byJobResponseIsEmptyWhenNotLoaded() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any()))
                .willReturn(new RegionJobStatisticsByJobSummary(null, List.of()));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "41110"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(content().string(containsString("\"statisticsMonth\":null")));
    }

    @Test
    @DisplayName("존재하지 않는 시군구면 404 와 ADDRESS_CODE_NOT_FOUND 를 반환한다")
    void byJobReturns404WhenSigunguMissing() throws Exception {
        given(regionJobStatisticsDetailService.byJob(any()))
                .willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"));

        mockMvc.perform(get("/api/detail/jobStatistics/byJob").param("sigunguCode", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_CODE_NOT_FOUND"));
    }
}
