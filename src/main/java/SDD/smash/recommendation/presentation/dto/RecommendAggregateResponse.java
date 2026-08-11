package SDD.smash.recommendation.presentation.dto;

import SDD.smash.recommendation.application.dto.RegionRecommendation;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 추천 목록 + AI 추천 응답. As-Is {@code Apis.Dto.RecommendAggregateResponse} 를 그대로 옮긴 것이다.
 *
 * <p>{@code items} 의 타입이 presentation dto가 아니라 application dto({@code RegionRecommendation})다.
 * {@code RecommendController} 가 유스케이스의 반환값을 가공 없이 그대로
 * {@code OpenAI.Service.AiRecommendService.summarize(List)} 에 넘기기 때문에 강제된
 * 타협이다 — {@code RegionRecommendation} 의 클래스 주석에 근거를 적었다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendAggregateResponse {

    private List<RegionRecommendation> items;
    private List<AiPickEntry> aiPick;
}
