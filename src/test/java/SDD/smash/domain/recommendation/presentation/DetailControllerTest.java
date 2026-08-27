package SDD.smash.domain.recommendation.presentation;

import SDD.smash.domain.recommendation.application.RegionDetailService;
import SDD.smash.domain.recommendation.application.dto.DwellingInfoSummary;
import SDD.smash.domain.recommendation.application.dto.DwellingTypeItem;
import SDD.smash.domain.recommendation.application.dto.JobVacancyItem;
import SDD.smash.domain.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.domain.recommendation.application.dto.RegionJobProfileItem;
import SDD.smash.domain.recommendation.application.port.out.RegionSummaryProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지역 상세 응답에 채용공고 카드 목록이 실리는지 확인한다. 보안/레이트리밋 필터는 끄고
 * (addFilters=false) application Service 는 목으로 대체한다.
 */
@WebMvcTest(controllers = DetailController.class)
@AutoConfigureMockMvc(addFilters = false)
class DetailControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegionDetailService regionDetailService;

    @MockitoBean
    RegionSummaryProvider regionSummaryProvider;

    @Test
    @DisplayName("상세 응답에 채용공고 카드 목록(jobVacancies)이 실린다")
    void detailResponseCarriesJobVacancies() throws Exception {
        // given
        JobVacancyItem item = new JobVacancyItem(
                "46203390", "백엔드 개발자", "스매시", "https://saramin/1",
                "서울 > 강남구", "웹개발", "회사내규", "신입", "대졸", "정규직",
                true, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        RegionJobProfileItem profile = new RegionJobProfileItem(
                4000, 0.4,
                List.of(new RegionJobProfileItem.IndustryShareItem("IT·웹·통신", 12)),
                80, 55);
        RegionDetailInfo info = RegionDetailInfo.builder()
                .sigunguCode("11680")
                .sigunguName("강남구")
                .jobVacancies(List.of(item))
                .regionJobProfile(profile)
                .build();
        given(regionDetailService.details(any(), any())).willReturn(info);

        // when & then
        mockMvc.perform(get("/api/detail").param("sigunguCode", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobVacancies[0].postingId").value("46203390"))
                .andExpect(jsonPath("$.jobVacancies[0].title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.jobVacancies[0].companyName").value("스매시"))
                .andExpect(jsonPath("$.jobVacancies[0].salaryText").value("회사내규"))
                .andExpect(jsonPath("$.jobVacancies[0].active").value(true))
                .andExpect(jsonPath("$.regionJobProfile.salaryMedianManwon").value(4000))
                .andExpect(jsonPath("$.regionJobProfile.newcomerRatio").value(0.4))
                .andExpect(jsonPath("$.regionJobProfile.topIndustries[0].name").value("IT·웹·통신"))
                .andExpect(jsonPath("$.regionJobProfile.sampleSize").value(80));
    }

    @Test
    @DisplayName("상세 응답에 통합 시세와 주택유형별 시세(dwellingByType)가 함께 실린다")
    void detailResponseCarriesDwellingByType() throws Exception {
        // given
        RegionDetailInfo info = RegionDetailInfo.builder()
                .sigunguCode("11680")
                .dwellingInfo(new DwellingInfoSummary(70.5, 65, 25000.0, 24000))
                .dwellingByType(List.of(
                        new DwellingTypeItem("APARTMENT", 70.5, 65, 25000.0, 24000),
                        new DwellingTypeItem("MULTIPLEX_HOUSE", 45.0, 40, null, null)))
                .build();
        given(regionDetailService.details(any(), any())).willReturn(info);

        // when & then
        mockMvc.perform(get("/api/detail").param("sigunguCode", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dwellingInfo.monthMid").value(65))
                .andExpect(jsonPath("$.dwellingByType.length()").value(2))
                .andExpect(jsonPath("$.dwellingByType[0].housingType").value("APARTMENT"))
                .andExpect(jsonPath("$.dwellingByType[0].monthAvg").value(70.5))
                .andExpect(jsonPath("$.dwellingByType[0].jeonseMid").value(24000))
                .andExpect(jsonPath("$.dwellingByType[1].housingType").value("MULTIPLEX_HOUSE"))
                // 실거래가 없는 항목은 필드가 사라지지 않고 null 로 실린다(응답 스키마 고정)
                .andExpect(content().string(containsString("\"jeonseAvg\":null")));
    }

    @Test
    @DisplayName("유형별 시세가 없으면 dwellingByType 이 빈 배열로 내려간다")
    void detailResponseCarriesEmptyDwellingByTypeWhenAbsent() throws Exception {
        // given - 유형별 필드를 채우지 않은 기존 형태
        given(regionDetailService.details(any(), any()))
                .willReturn(RegionDetailInfo.builder().sigunguCode("11680").build());

        // when & then
        mockMvc.perform(get("/api/detail").param("sigunguCode", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dwellingByType.length()").value(0));
    }
}
