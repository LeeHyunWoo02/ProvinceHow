package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.RegionJobStatisticsTrendPoint;

/**
 * 지역 고용통계 추세 한 점(recommendation 로컬 요약 DTO).
 * job 의 {@link RegionJobStatisticsTrendPoint} 를 recommendation 소유 타입으로 재포장한다.
 *
 * @param jobOpeningRatio 구인배수. 그 달 유효구직자수 합계가 0 이면 {@code null}
 */
public record RegionJobStatisticsTrendPointSummary(String statisticsMonth,
                                                   long validOpenings,
                                                   long validSeekers,
                                                   Double jobOpeningRatio) {

    public static RegionJobStatisticsTrendPointSummary from(RegionJobStatisticsTrendPoint point) {
        return new RegionJobStatisticsTrendPointSummary(
                point.statisticsMonth(),
                point.validOpenings(),
                point.validSeekers(),
                point.jobOpeningRatio());
    }
}
