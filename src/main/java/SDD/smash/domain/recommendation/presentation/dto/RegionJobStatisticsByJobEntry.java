package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobItem;

/**
 * 직종 대분류별 고용행정통계 한 종. HTTP 응답 계약(JSON 필드명)의 소유자다.
 * 지표 필드는 상세 요약({@link RegionJobStatisticsEntry})과 동일하고 직종 식별자만 앞에 붙는다.
 *
 * @param isSelected      {@code midJobCode} 가 속한 대분류인지. 필드를 생략하지 않고 고르지 않은
 *                        항목에도 {@code false} 를 명시한다
 * @param jobOpeningRatio 구인배수. 유효구직자수가 0 이면 {@code null}
 * @param nonCapitalRank  <b>이 직종의</b> 비수도권 내 순위. 응답 최상단의
 *                        {@code totalNonCapitalRank}(13종 합계)와 다른 값이다. 수도권이면 {@code null}
 */
public record RegionJobStatisticsByJobEntry(String jobMajorCode,
                                            String jobMajorName,
                                            boolean isSelected,
                                            long validOpenings,
                                            long validSeekers,
                                            Double jobOpeningRatio,
                                            NonCapitalRankEntry nonCapitalRank,
                                            long jobOpenings,
                                            long jobSeekers,
                                            long placements) {

    public static RegionJobStatisticsByJobEntry from(RegionJobStatisticsByJobItem item) {
        return new RegionJobStatisticsByJobEntry(
                item.jobMajorCode(),
                item.jobMajorName(),
                item.isSelected(),
                item.validOpenings(),
                item.validSeekers(),
                item.jobOpeningRatio(),
                NonCapitalRankEntry.from(item.nonCapitalRank()),
                item.jobOpenings(),
                item.jobSeekers(),
                item.placements());
    }
}
