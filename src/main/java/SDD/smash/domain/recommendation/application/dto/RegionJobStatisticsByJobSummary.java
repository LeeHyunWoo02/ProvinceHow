package SDD.smash.domain.recommendation.application.dto;

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
 * @param statisticsMonth 기준월({@code YYYY-MM}). 적재된 행이 없으면(미적재/시 레벨) {@code null}
 * @param items           대분류별 통계. 적재된 행이 없으면 빈 목록
 */
public record RegionJobStatisticsByJobSummary(String statisticsMonth,
                                              List<RegionJobStatisticsByJobItem> items) {

    /** 대분류별 행을 코드 오름차순 목록으로 접는다. 적재 행이 없으면 기준월 없이 빈 목록이다. */
    public static RegionJobStatisticsByJobSummary from(List<RegionJobStatisticsView> views,
                                                       Map<String, String> jobMajorNameByCode) {
        if (views == null || views.isEmpty()) {
            return new RegionJobStatisticsByJobSummary(null, List.of());
        }

        List<RegionJobStatisticsByJobItem> items = views.stream()
                .sorted(Comparator.comparing(RegionJobStatisticsView::jobTopCode))
                .map(view -> RegionJobStatisticsByJobItem.from(
                        view, jobMajorNameByCode.get(view.jobTopCode())))
                .toList();

        return new RegionJobStatisticsByJobSummary(views.get(0).statisticsMonth(), items);
    }
}
