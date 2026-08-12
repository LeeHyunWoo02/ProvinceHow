package SDD.smash.domain.recommendation.application.port.in;

import SDD.smash.domain.recommendation.application.dto.RecommendCommand;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;

import java.util.List;

/** 지역 추천 in-port. As-Is {@code RecommendService.recommend} 자리다. */
public interface RecommendRegionUseCase {

    List<RegionRecommendation> recommend(RecommendCommand command);
}
