package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;

import java.util.List;

/**
 * 직종 대분류 분해 조회 응답. 미적재/시 레벨이면 {@code statisticsMonth} 는 null,
 * {@code items} 는 빈 배열이다(값을 0 으로 채우지 않는다).
 *
 * @param statisticsMonth     기준월({@code YYYY-MM}). 적재 행이 없으면 {@code null}
 * @param selectedJobTopCode  {@code midJobCode} 가 속한 대분류 코드. 고르지 않았으면 {@code null}.
 *                            미적재 지역에서도 선택이 유지되도록 items 와 별개로 싣는다
 * @param totalNonCapitalRank 직종 <b>13종 합계</b> 구인배수의 비수도권 내 순위.
 *                            item 의 {@code nonCapitalRank}(직종별)와 다른 값이다
 */
public record RegionJobStatisticsByJobResponse(String statisticsMonth,
                                               String selectedJobTopCode,
                                               NonCapitalRankEntry totalNonCapitalRank,
                                               List<RegionJobStatisticsByJobEntry> items) {

    public static RegionJobStatisticsByJobResponse from(RegionJobStatisticsByJobSummary summary) {
        return new RegionJobStatisticsByJobResponse(
                summary.statisticsMonth(),
                summary.selectedJobTopCode(),
                NonCapitalRankEntry.from(summary.totalNonCapitalRank()),
                summary.items().stream()
                        .map(RegionJobStatisticsByJobEntry::from)
                        .toList());
    }
}
