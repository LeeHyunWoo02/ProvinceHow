package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 비수도권 시군구 안에서의 구인배수 순위.
 *
 * <p>원시 구인배수({@code 0.092})만으로는 좋고 나쁨을 판단할 기준이 없다. 비수도권 173개
 * 시군구의 분포가 최소 0.024 ~ 최대 0.903 으로 넓어, 사용자가 읽을 수 있는 형태는 원시값이
 * 아니라 <b>분포 안에서의 위치</b>다.
 *
 * @param percentile 백분위(0~100). <b>클수록 구인배수가 높다</b> — 값이 클수록 좋은 지표다
 * @param topPercent 상위 N%. {@code rank / total} 이며 <b>작을수록 좋다</b>.
 *                   1위여도 "상위 0%" 는 말이 되지 않으므로 최소 1 이다
 * @param rank       비수도권 내 순위(1 = 구인배수 최고)
 * @param total      비교 모집단 크기(구인배수를 계산할 수 있는 비수도권 시군구 수)
 */
public record NonCapitalRank(int percentile, int topPercent, int rank, int total) {

    public NonCapitalRank {
        if (total < 1) {
            throw new DomainException(ErrorCode.JOB_STATISTICS_INVALID, "비교 모집단은 1 이상이어야 합니다.");
        }
        if (rank < 1 || rank > total) {
            throw new DomainException(ErrorCode.JOB_STATISTICS_INVALID, "순위는 1 이상 모집단 크기 이하여야 합니다.");
        }
        if (percentile < 0 || percentile > 100) {
            throw new DomainException(ErrorCode.JOB_STATISTICS_INVALID, "백분위는 0~100 범위여야 합니다.");
        }
        if (topPercent < 1 || topPercent > 100) {
            throw new DomainException(ErrorCode.JOB_STATISTICS_INVALID, "상위 비율은 1~100 범위여야 합니다.");
        }
    }
}
