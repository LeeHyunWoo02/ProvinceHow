package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;

import java.util.List;

/**
 * 직종 대분류 분해 조회 응답. 미적재/시 레벨이면 {@code statisticsMonth} 는 null,
 * {@code items} 는 빈 배열이다(값을 0 으로 채우지 않는다).
 *
 * @param statisticsMonth 기준월({@code YYYY-MM}). 적재 행이 없으면 {@code null}
 */
public record RegionJobStatisticsByJobResponse(String statisticsMonth,
                                               List<RegionJobStatisticsByJobEntry> items) {

    public static RegionJobStatisticsByJobResponse from(RegionJobStatisticsByJobSummary summary) {
        return new RegionJobStatisticsByJobResponse(
                summary.statisticsMonth(),
                summary.items().stream()
                        .map(RegionJobStatisticsByJobEntry::from)
                        .toList());
    }
}
