package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.NonCapitalRankView;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;

/**
 * 직종 대분류 한 종의 고용행정통계(recommendation 로컬 요약 DTO).
 * job 의 {@link RegionJobStatisticsView} 를 recommendation 소유 타입으로 재포장하며,
 * 직종명·선택 여부·직종별 비수도권 백분위를 덧붙인다.
 *
 * <p>지표 이름·타입·null 규칙은 상세 요약({@code RegionJobStatisticsSummary})과 같다.
 *
 * @param jobMajorCode    직종 대분류 코드(=jobTopCode, 01~13)
 * @param jobMajorName    직종 대분류명. 코드-이름 맵에 없으면 {@code null}
 * @param isSelected      사용자가 고른 중분류가 속한 대분류인지. 고르지 않았으면 전부 {@code false}
 * @param jobOpeningRatio 구인배수. 유효구직자수가 0 이면 {@code null}
 * @param nonCapitalRank  <b>이 직종의</b> 구인배수를 비수도권 안에서 잰 순위.
 *                        13종 합계 기준({@code totalNonCapitalRank})과는 다른 값이다.
 *                        수도권 지역이거나 배수가 없으면 {@code null}
 */
public record RegionJobStatisticsByJobItem(String jobMajorCode,
                                           String jobMajorName,
                                           boolean isSelected,
                                           long validOpenings,
                                           long validSeekers,
                                           Double jobOpeningRatio,
                                           NonCapitalRankSummary nonCapitalRank,
                                           long jobOpenings,
                                           long jobSeekers,
                                           long placements) {

    public static RegionJobStatisticsByJobItem from(RegionJobStatisticsView view,
                                                    String jobMajorName,
                                                    boolean selected,
                                                    NonCapitalRankView nonCapitalRank) {
        return new RegionJobStatisticsByJobItem(
                view.jobTopCode(),
                jobMajorName,
                selected,
                view.validOpenings(),
                view.validSeekers(),
                view.jobOpeningRatio(),
                NonCapitalRankSummary.from(nonCapitalRank),
                view.jobOpenings(),
                view.jobSeekers(),
                view.placements());
    }
}
