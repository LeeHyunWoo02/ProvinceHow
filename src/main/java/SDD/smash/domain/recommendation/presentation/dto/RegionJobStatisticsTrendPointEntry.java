package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsTrendPointSummary;

/**
 * 지역 고용통계 추세 한 점. HTTP 응답 계약(JSON 필드명)의 소유자다.
 * 추세 화면이 쓰는 세 지표({@code validOpenings}, {@code validSeekers}, {@code jobOpeningRatio})만 싣는다.
 *
 * @param jobOpeningRatio 구인배수. 그 달 유효구직자수 합계가 0 이면 {@code null}
 */
public record RegionJobStatisticsTrendPointEntry(String statisticsMonth,
                                                 long validOpenings,
                                                 long validSeekers,
                                                 Double jobOpeningRatio) {

    public static RegionJobStatisticsTrendPointEntry from(RegionJobStatisticsTrendPointSummary point) {
        return new RegionJobStatisticsTrendPointEntry(
                point.statisticsMonth(),
                point.validOpenings(),
                point.validSeekers(),
                point.jobOpeningRatio());
    }
}
