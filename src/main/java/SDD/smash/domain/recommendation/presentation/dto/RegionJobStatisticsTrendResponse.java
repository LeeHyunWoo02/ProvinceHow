package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsTrendPointSummary;

import java.util.List;

/**
 * 지역 고용통계 추세 조회 응답. 월 오름차순 시계열이다.
 * 미적재/시 레벨이면 {@code points} 가 빈 배열이다(빈 월을 채우지 않는다).
 */
public record RegionJobStatisticsTrendResponse(String sigunguCode,
                                               List<RegionJobStatisticsTrendPointEntry> points) {

    public static RegionJobStatisticsTrendResponse of(String sigunguCode,
                                                      List<RegionJobStatisticsTrendPointSummary> points) {
        return new RegionJobStatisticsTrendResponse(
                sigunguCode,
                points.stream()
                        .map(RegionJobStatisticsTrendPointEntry::from)
                        .toList());
    }
}
