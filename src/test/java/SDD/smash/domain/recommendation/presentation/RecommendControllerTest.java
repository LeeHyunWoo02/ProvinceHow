package SDD.smash.domain.recommendation.presentation;

import SDD.smash.domain.recommendation.application.RecommendRegionService;
import SDD.smash.domain.recommendation.application.dto.NonCapitalRankSummary;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsSummary;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;
import SDD.smash.domain.recommendation.application.port.out.RegionPickProvider;
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

@WebMvcTest(controllers = RecommendController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecommendControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RecommendRegionService recommendRegionService;

    @MockitoBean
    RegionPickProvider regionPickProvider;

    private static final String BASE = "/api/recommend?supportChoice=0&dwellingType=MONTHLY&price=50&infraChoice=0";

    @Test
    @DisplayName("추천 목록 각 item 에 상세와 동일한 jobStatistics 요약이 실린다")
    void recommendItemsCarryJobStatistics() throws Exception {
        RegionRecommendation withStat = RegionRecommendation.builder()
                .sigunguCode("11680")
                .score(90)
                .jobStatistics(new RegionJobStatisticsSummary(
                        "2026-07", 500L, 2000L, 0.25, 150L, 600L, 50L,
                        new NonCapitalRankSummary(72, 28, 49, 173)))
                .build();
        RegionRecommendation withoutStat = RegionRecommendation.builder()
                .sigunguCode("41110")   // 미적재/시 레벨 → jobStatistics null
                .score(80)
                .build();
        given(recommendRegionService.recommend(any())).willReturn(List.of(withStat, withoutStat));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sigunguCode").value("11680"))
                .andExpect(jsonPath("$.items[0].jobStatistics.statisticsMonth").value("2026-07"))
                .andExpect(jsonPath("$.items[0].jobStatistics.validOpenings").value(500))
                .andExpect(jsonPath("$.items[0].jobStatistics.jobOpeningRatio").value(0.25))
                .andExpect(jsonPath("$.items[0].jobStatistics.placements").value(50))
                .andExpect(jsonPath("$.items[0].jobStatistics.nonCapitalRank.topPercent").value(28))
                // 미적재 지역은 jobStatistics 가 null 이다
                .andExpect(jsonPath("$.items[1].jobStatistics").doesNotExist())
                .andExpect(content().string(containsString("\"jobStatistics\":null")));
    }

    @Test
    @DisplayName("구직자 0 이면 jobStatistics.jobOpeningRatio 만 null 로 실린다")
    void recommendItemJobOpeningRatioNullWhenNoSeekers() throws Exception {
        given(recommendRegionService.recommend(any())).willReturn(List.of(
                RegionRecommendation.builder()
                        .sigunguCode("11680")
                        .jobStatistics(new RegionJobStatisticsSummary(
                                "2026-07", 40L, 0L, null, 10L, 0L, 0L, null))
                        .build()));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobStatistics.validOpenings").value(40))
                .andExpect(content().string(containsString("\"jobOpeningRatio\":null")));
    }

    @Test
    @DisplayName("aiUse=true 재조립 경로에서도 jobStatistics 가 유실되지 않는다")
    void jobStatisticsSurvivesAiRebuild() throws Exception {
        given(recommendRegionService.recommend(any())).willReturn(List.of(
                RegionRecommendation.builder()
                        .sigunguCode("11680")
                        .jobStatistics(new RegionJobStatisticsSummary(
                                "2026-07", 500L, 2000L, 0.25, 150L, 600L, 50L,
                        new NonCapitalRankSummary(72, 28, 49, 173)))
                        .build()));
        given(regionPickProvider.pick(any())).willReturn(List.of());

        mockMvc.perform(get(BASE + "&aiUse=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobStatistics.statisticsMonth").value("2026-07"))
                .andExpect(jsonPath("$.items[0].jobStatistics.validSeekers").value(2000));
    }

    @Test
    @DisplayName("aiUse=true 인데 AI 픽이 빈 목록이어도 200 과 함께 items 는 그대로 실린다")
    void keeps200AndItemsWhenAiPickEmpty() throws Exception {
        // given - AI 어댑터가 실패 시 폴백으로 빈 목록을 반환하는 상황
        given(recommendRegionService.recommend(any())).willReturn(List.of(
                RegionRecommendation.builder()
                        .sigunguCode("11680")
                        .sigunguName("강남구")
                        .score(90)
                        .build()));
        given(regionPickProvider.pick(any())).willReturn(List.of());

        // when & then
        mockMvc.perform(get(BASE + "&aiUse=true"))
                .andExpect(status().isOk())
                // AI 결과가 없어도 non-AI 필드(추천 목록)는 정상 조립된다
                .andExpect(jsonPath("$.items[0].sigunguCode").value("11680"))
                .andExpect(jsonPath("$.items[0].sigunguName").value("강남구"))
                .andExpect(jsonPath("$.items[0].score").value(90))
                // AI 실패는 aiPick 만 빈 배열로 흡수된다
                .andExpect(jsonPath("$.aiPick.length()").value(0));
    }

    @Test
    @DisplayName("aiUse=true 인데 AI 픽이 null 이어도 200 과 함께 aiPick 은 빈 배열로 실린다")
    void keeps200AndItemsWhenAiPickNull() throws Exception {
        // given - AI 어댑터 폴백이 null 을 돌려주는 상황
        given(recommendRegionService.recommend(any())).willReturn(List.of(
                RegionRecommendation.builder()
                        .sigunguCode("11680")
                        .score(90)
                        .build()));
        given(regionPickProvider.pick(any())).willReturn(null);

        // when & then
        mockMvc.perform(get(BASE + "&aiUse=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sigunguCode").value("11680"))
                .andExpect(jsonPath("$.items[0].score").value(90))
                // null 은 AiConverter 가 빈 배열로 정규화한다
                .andExpect(jsonPath("$.aiPick.length()").value(0));
    }
}
