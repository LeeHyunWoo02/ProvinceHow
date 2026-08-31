package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsSummary;

/**
 * 지역 상세 응답에 실리는 고용행정통계 요약. HTTP 응답 계약(JSON 필드명)의 소유자다.
 * recommendation 로컬 요약({@link RegionJobStatisticsSummary})을 표현 계층 타입으로 옮긴 것이다.
 *
 * @param statisticsMonth 기준월({@code YYYY-MM}). 화면의 "YYYY년 M월 기준" 표기용
 * @param jobOpeningRatio 구인배수. 유효구직자수가 0 이면 {@code null}
 * @param nonCapitalRank  구인배수의 비수도권 내 순위. 수도권 지역이거나 배수가 없으면 {@code null}
 */
public record RegionJobStatisticsEntry(String statisticsMonth,
                                       long validOpenings,
                                       long validSeekers,
                                       Double jobOpeningRatio,
                                       long jobOpenings,
                                       long jobSeekers,
                                       long placements,
                                       NonCapitalRankEntry nonCapitalRank) {

    public static RegionJobStatisticsEntry from(RegionJobStatisticsSummary summary) {
        if (summary == null) {
            return null;
        }
        return new RegionJobStatisticsEntry(
                summary.statisticsMonth(),
                summary.validOpenings(),
                summary.validSeekers(),
                summary.jobOpeningRatio(),
                summary.jobOpenings(),
                summary.jobSeekers(),
                summary.placements(),
                NonCapitalRankEntry.from(summary.nonCapitalRank()));
    }
}
