package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;

import java.util.List;

/**
 * 지역 상세에 실리는 고용행정통계 요약(recommendation 로컬 요약 DTO).
 * job 컨텍스트의 {@link RegionJobStatisticsView} 를 recommendation 소유 타입으로 재포장한다
 * ({@code JobInfoSummary.from}, {@code RegionJobProfileItem.from} 과 같은 관례).
 *
 * <p>원본은 시군구 x 직종 대분류 단위라 여러 행으로 온다. 여기서는 <b>시군구 합계</b>로 접는다.
 * 직종별 분해는 상세 첫 화면에 필요하지 않아 싣지 않는다.
 *
 * @param statisticsMonth 기준월({@code YYYY-MM}). 월 1회 갱신이라 표기가 없으면 실시간 값으로
 *                        오해되므로 <b>항상 채워진다</b>
 * @param jobOpeningRatio 구인배수(유효구인인원 / 유효구직자수). 유효구직자수가 0 이면 {@code null}
 * @param jobOpenings     구인인원(월). 그 달에 새로 올라온 구인 규모로 유효구인인원과 개념이 다르다
 */
public record RegionJobStatisticsSummary(String statisticsMonth,
                                         long validOpenings,
                                         long validSeekers,
                                         Double jobOpeningRatio,
                                         long jobOpenings,
                                         long jobSeekers,
                                         long placements) {

    /** 직종 대분류별 행을 시군구 합계로 접는다. 적재된 행이 없으면 {@code null}(= 통계 없음). */
    public static RegionJobStatisticsSummary from(List<RegionJobStatisticsView> views) {
        if (views == null || views.isEmpty()) {
            return null;
        }

        long validOpenings = 0L;
        long validSeekers = 0L;
        long jobOpenings = 0L;
        long jobSeekers = 0L;
        long placements = 0L;
        for (RegionJobStatisticsView view : views) {
            validOpenings += view.validOpenings();
            validSeekers += view.validSeekers();
            jobOpenings += view.jobOpenings();
            jobSeekers += view.jobSeekers();
            placements += view.placements();
        }

        // 구인배수는 대분류별 배수의 평균이 아니라 합계로 다시 나눈 값이다.
        // 구직자가 0 이면 배수가 성립하지 않으므로 0 이 아니라 값 없음이다.
        Double ratio = (validSeekers == 0L) ? null : (double) validOpenings / (double) validSeekers;

        return new RegionJobStatisticsSummary(
                views.get(0).statisticsMonth(),
                validOpenings, validSeekers, ratio,
                jobOpenings, jobSeekers, placements);
    }
}
