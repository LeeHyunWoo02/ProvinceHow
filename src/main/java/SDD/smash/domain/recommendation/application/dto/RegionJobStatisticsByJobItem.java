package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;

/**
 * 직종 대분류 한 종의 고용행정통계(recommendation 로컬 요약 DTO).
 * job 의 {@link RegionJobStatisticsView} 를 recommendation 소유 타입으로 재포장하며,
 * 직종명({@code jobMajorName})만 대분류 코드→이름 맵으로 덧붙인다.
 *
 * <p>지표 이름·타입·null 규칙은 상세 요약({@code RegionJobStatisticsSummary})과 같다.
 *
 * @param jobMajorCode    직종 대분류 코드(=jobTopCode, 01~13)
 * @param jobMajorName    직종 대분류명. 코드-이름 맵에 없으면 {@code null}
 * @param jobOpeningRatio 구인배수. 유효구직자수가 0 이면 {@code null}
 */
public record RegionJobStatisticsByJobItem(String jobMajorCode,
                                           String jobMajorName,
                                           long validOpenings,
                                           long validSeekers,
                                           Double jobOpeningRatio,
                                           long jobOpenings,
                                           long jobSeekers,
                                           long placements) {

    public static RegionJobStatisticsByJobItem from(RegionJobStatisticsView view, String jobMajorName) {
        return new RegionJobStatisticsByJobItem(
                view.jobTopCode(),
                jobMajorName,
                view.validOpenings(),
                view.validSeekers(),
                view.jobOpeningRatio(),
                view.jobOpenings(),
                view.jobSeekers(),
                view.placements());
    }
}
