package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.NonCapitalRankView;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 최신 기준월 한 시군구의 직종 대분류별 고용행정통계(recommendation 로컬 요약 DTO).
 * 상세 첫 화면의 시군구 합계({@code RegionJobStatisticsSummary})와 달리 대분류 13종으로 분해한다.
 *
 * <p>{@code items} 는 <b>jobMajorCode 오름차순</b>으로 결정적 정렬한다(프론트가 응답 순서를 표시
 * 순서로 신뢰한다). 코드가 2자리 고정폭이라 문자열 오름차순이 곧 숫자 오름차순이다.
 *
 * @param statisticsMonth     기준월({@code YYYY-MM}). 적재된 행이 없으면(미적재/시 레벨) {@code null}
 * @param selectedJobMajorCode  사용자가 고른 중분류가 속한 대분류 코드. 고르지 않았으면 {@code null}.
 *                            미적재 지역에서도 선택이 유지되도록 items 와 별개로 싣는다
 * @param totalNonCapitalRank <b>직종 13종 합계</b> 구인배수의 비수도권 내 순위.
 *                            item 의 {@code nonCapitalRank}(직종별)와 다른 값이라 이름을 갈라 둔다.
 *                            수도권 지역이거나 배수가 없으면 {@code null}
 * @param items               대분류별 통계. 적재된 행이 없으면 빈 목록
 */
public record RegionJobStatisticsByJobSummary(String statisticsMonth,
                                              String selectedJobMajorCode,
                                              NonCapitalRankSummary totalNonCapitalRank,
                                              List<RegionJobStatisticsByJobItem> items) {

    /** 통계가 적재돼 있지 않은 상태. 고른 대분류는 있으면 그대로 실어 보낸다. */
    public static RegionJobStatisticsByJobSummary empty(String selectedJobMajorCode) {
        return new RegionJobStatisticsByJobSummary(null, selectedJobMajorCode, null, List.of());
    }

    /** 대분류별 행을 코드 오름차순 목록으로 접는다. 적재 행이 없으면 기준월 없이 빈 목록이다. */
    public static RegionJobStatisticsByJobSummary from(List<RegionJobStatisticsView> views,
                                                       Map<String, String> jobMajorNameByCode,
                                                       String selectedJobMajorCode,
                                                       NonCapitalRankView totalNonCapitalRank,
                                                       Map<String, NonCapitalRankView> rankByJobMajorCode) {
        if (views == null || views.isEmpty()) {
            return empty(selectedJobMajorCode);
        }

        List<RegionJobStatisticsByJobItem> items = views.stream()
                .sorted(Comparator.comparing(RegionJobStatisticsView::jobTopCode))
                .map(view -> RegionJobStatisticsByJobItem.from(
                        view,
                        jobMajorNameByCode.get(view.jobTopCode()),
                        view.jobTopCode().equals(selectedJobMajorCode),
                        rankByJobMajorCode.get(view.jobTopCode())))
                .toList();

        return new RegionJobStatisticsByJobSummary(
                views.get(0).statisticsMonth(),
                selectedJobMajorCode,
                NonCapitalRankSummary.from(totalNonCapitalRank),
                items);
    }
}
