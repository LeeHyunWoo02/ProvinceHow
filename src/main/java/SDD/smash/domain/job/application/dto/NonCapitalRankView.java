package SDD.smash.domain.job.application.dto;

import SDD.smash.domain.job.domain.model.NonCapitalRank;

/**
 * 비수도권 내 구인배수 순위 조회 결과.
 *
 * @param percentile 백분위(0~100). 클수록 구인배수가 높다
 * @param topPercent 상위 N%. 작을수록 좋다
 * @param rank       비수도권 내 순위(1 = 최고)
 * @param total      비교 모집단 크기
 */
public record NonCapitalRankView(int percentile, int topPercent, int rank, int total) {

    public static NonCapitalRankView from(NonCapitalRank rank) {
        return new NonCapitalRankView(rank.percentile(), rank.topPercent(), rank.rank(), rank.total());
    }
}
