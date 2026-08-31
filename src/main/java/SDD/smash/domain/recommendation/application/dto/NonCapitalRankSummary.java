package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.NonCapitalRankView;

/**
 * 비수도권 내 구인배수 순위(recommendation 로컬 요약 DTO).
 * job 의 {@link NonCapitalRankView} 를 recommendation 소유 타입으로 재포장한다.
 *
 * @param percentile 백분위(0~100). 클수록 구인배수가 높다
 * @param topPercent 상위 N%. 작을수록 좋다
 * @param rank       비수도권 내 순위(1 = 최고)
 * @param total      비교 모집단 크기
 */
public record NonCapitalRankSummary(int percentile, int topPercent, int rank, int total) {

    public static NonCapitalRankSummary from(NonCapitalRankView view) {
        if (view == null) {
            return null;
        }
        return new NonCapitalRankSummary(view.percentile(), view.topPercent(), view.rank(), view.total());
    }
}
