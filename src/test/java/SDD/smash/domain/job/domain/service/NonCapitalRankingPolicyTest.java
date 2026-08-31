package SDD.smash.domain.job.domain.service;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.NonCapitalRank;
import SDD.smash.domain.job.domain.model.NonCapitalRatioSnapshot;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 비수도권 백분위 규칙. Spring 없이 순수하게 돈다. */
class NonCapitalRankingPolicyTest {

    private static final StatisticsMonth MONTH = StatisticsMonth.of("2026-07");

    private final NonCapitalRankingPolicy policy = new NonCapitalRankingPolicy();

    @Test
    @DisplayName("서울·경기·인천은 수도권으로 판정한다")
    void detectsCapitalAreaBySidoCode() {
        assertThat(policy.isCapitalArea(SigunguCode.of("11680"))).isTrue();   // 서울 강남구
        assertThat(policy.isCapitalArea(SigunguCode.of("41110"))).isTrue();   // 경기 수원시
        assertThat(policy.isCapitalArea(SigunguCode.of("28110"))).isTrue();   // 인천 중구
        assertThat(policy.isCapitalArea(SigunguCode.of("46110"))).isFalse();  // 전남 목포시
    }

    @Test
    @DisplayName("비수도권 지역은 분포 안에서의 백분위와 상위 N% 를 받는다")
    void ranksNonCapitalRegionWithinDistribution() {
        // given - 배수 0.1 / 0.2 / 0.3 / 0.4 네 지역
        Map<SigunguCode, Double> distribution = distribution(
                "46110", 0.1, "46130", 0.2, "46150", 0.3, "46170", 0.4);

        // when - 위에서 두 번째(0.3)
        NonCapitalRank rank = policy.rankOf(SigunguCode.of("46150"), distribution).orElseThrow();

        // then - 아래로 2개, 동점은 자기 자신뿐이므로 (2 + 0.5) / 4 = 62.5 -> 63
        assertThat(rank.percentile()).isEqualTo(63);
        assertThat(rank.rank()).isEqualTo(2);
        assertThat(rank.total()).isEqualTo(4);
        assertThat(rank.topPercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("구인배수가 가장 높은 지역이 가장 높은 백분위를 받는다")
    void givesHighestPercentileToHighestRatio() {
        Map<SigunguCode, Double> distribution = distribution(
                "46110", 0.1, "46130", 0.2, "46150", 0.3, "46170", 0.9);

        NonCapitalRank best = policy.rankOf(SigunguCode.of("46170"), distribution).orElseThrow();
        NonCapitalRank worst = policy.rankOf(SigunguCode.of("46110"), distribution).orElseThrow();

        assertThat(best.percentile()).isGreaterThan(worst.percentile());
        assertThat(best.rank()).isEqualTo(1);
        assertThat(best.topPercent()).isEqualTo(25);
    }

    @Test
    @DisplayName("수도권 지역은 비수도권 모집단 밖이라 백분위가 없다")
    void leavesRankEmptyForCapitalAreaRegion() {
        // given - 분포에는 애초에 수도권이 없다
        NonCapitalRatioSnapshot snapshot = policy.snapshot(MONTH, List.of(
                statistics("11680", "01", 500L, 1_000L),   // 서울 강남구
                statistics("46110", "01", 100L, 1_000L)));

        // when
        Optional<NonCapitalRank> rank = policy.rankOf(SigunguCode.of("11680"), snapshot.totalRatios());

        // then
        assertThat(rank).isEmpty();
        assertThat(snapshot.totalRatios()).containsOnlyKeys(SigunguCode.of("46110"));
    }

    @Test
    @DisplayName("구인배수를 계산할 수 없으면(유효구직자수 0) 백분위도 없다")
    void leavesRankEmptyWhenRatioIsNotComputable() {
        // given - 46130 은 구직자가 0 이라 배수가 성립하지 않는다
        NonCapitalRatioSnapshot snapshot = policy.snapshot(MONTH, List.of(
                statistics("46110", "01", 100L, 1_000L),
                statistics("46130", "01", 100L, 0L)));

        // when & then
        assertThat(policy.rankOf(SigunguCode.of("46130"), snapshot.totalRatios())).isEmpty();
        assertThat(policy.rankOf(SigunguCode.of("46110"), snapshot.totalRatios())).isPresent();
    }

    @Test
    @DisplayName("분포는 시군구 합계와 직종 대분류별로 따로 접힌다")
    void foldsDistributionByRegionAndByJob() {
        // given - 한 지역의 두 직종
        NonCapitalRatioSnapshot snapshot = policy.snapshot(MONTH, List.of(
                statistics("46110", "02", 200L, 100L),
                statistics("46110", "06", 100L, 300L)));

        // then - 합계는 300/400, 직종별은 각각 따로
        assertThat(snapshot.totalRatios().get(SigunguCode.of("46110"))).isEqualTo(0.75);
        assertThat(snapshot.ratiosOf(JobCode.of("02")).get(SigunguCode.of("46110"))).isEqualTo(2.0);
        assertThat(snapshot.ratiosOf(JobCode.of("06")).get(SigunguCode.of("46110"))).isEqualTo(1.0 / 3.0);
        assertThat(snapshot.ratiosOf(JobCode.of("13"))).isEmpty();
    }

    @Test
    @DisplayName("통계 행이 없으면 빈 분포이고 백분위도 나오지 않는다")
    void producesEmptySnapshotWhenNoRows() {
        NonCapitalRatioSnapshot snapshot = policy.snapshot(MONTH, List.of());

        assertThat(snapshot.isEmpty()).isTrue();
        assertThat(policy.rankOf(SigunguCode.of("46110"), snapshot.totalRatios())).isEmpty();
        assertThat(policy.percentiles(snapshot.totalRatios())).isEmpty();
    }

    @Test
    @DisplayName("분포 전체를 백분위 맵으로 바꾸면 모든 지역이 0~100 안에 들어간다")
    void convertsWholeDistributionIntoPercentiles() {
        Map<SigunguCode, Double> distribution = distribution(
                "46110", 0.024, "46130", 0.129, "46150", 0.903);

        Map<SigunguCode, Integer> percentiles = policy.percentiles(distribution);

        assertThat(percentiles).hasSize(3);
        assertThat(percentiles.values()).allMatch(value -> value >= 0 && value <= 100);
        assertThat(percentiles.get(SigunguCode.of("46150")))
                .isGreaterThan(percentiles.get(SigunguCode.of("46110")));
    }

    private Map<SigunguCode, Double> distribution(Object... codeAndRatio) {
        Map<SigunguCode, Double> distribution = new LinkedHashMap<>();
        for (int i = 0; i < codeAndRatio.length; i += 2) {
            distribution.put(SigunguCode.of((String) codeAndRatio[i]), (Double) codeAndRatio[i + 1]);
        }
        return distribution;
    }

    private RegionJobStatistics statistics(String sigunguCode, String jobTopCode,
                                           long validOpenings, long validSeekers) {
        return RegionJobStatistics.of(
                new RegionJobStatisticsKey(SigunguCode.of(sigunguCode), JobCode.of(jobTopCode), MONTH),
                0L, 0L, 0L, validOpenings, validSeekers);
    }
}
