package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.NonCapitalRankSummary;

/**
 * 비수도권 내 구인배수 순위. HTTP 응답 계약(JSON 필드명)의 소유자다.
 *
 * @param percentile 백분위(0~100). 클수록 구인배수가 높다
 * @param topPercent 상위 N%. 작을수록 좋다
 * @param rank       비수도권 내 순위(1 = 최고)
 * @param total      비교 모집단 크기
 */
public record NonCapitalRankEntry(int percentile, int topPercent, int rank, int total) {

    public static NonCapitalRankEntry from(NonCapitalRankSummary summary) {
        if (summary == null) {
            return null;
        }
        return new NonCapitalRankEntry(
                summary.percentile(), summary.topPercent(), summary.rank(), summary.total());
    }
}
