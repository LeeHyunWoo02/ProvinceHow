package SDD.smash.domain.infra.domain.service;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraScore;
import SDD.smash.domain.infra.domain.model.RatioBasis;
import SDD.smash.domain.infra.domain.model.RegionIndustryCount;
import SDD.smash.domain.infra.domain.model.RegionIndustryStat;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InfraStatPolicyTest {

    private static final IndustryCode RESTAURANT = IndustryCode.of("RESTAURANT");
    private static final IndustryCode PHARMACY = IndustryCode.of("PHARMACY");

    private final InfraStatPolicy policy = new InfraStatPolicy();

    private static RegionIndustryCount count(String sigungu, IndustryCode industry, int count) {
        return new RegionIndustryCount(SigunguCode.of(sigungu), industry, count);
    }

    private static Map<String, RegionIndustryStat> byRegion(List<RegionIndustryStat> stats, IndustryCode industry) {
        return stats.stream()
                .filter(stat -> stat.industryCode().equals(industry))
                .collect(Collectors.toMap(stat -> stat.sigunguCode().value(), stat -> stat));
    }

    // ------------------------------------------------------------------ ratio

    @Test
    @DisplayName("기본 기준(PERCENT)에서 ratio 는 시군구 내 업종 구성비를 0~100 으로 나타낸다")
    void computesRatioAsPercentOfRegionTotal() {
        List<RegionIndustryStat> stats = policy.stats(List.of(
                count("11110", RESTAURANT, 75),
                count("11110", PHARMACY, 25)));

        assertThat(byRegion(stats, RESTAURANT).get("11110").ratio().value()).isEqualByComparingTo("75.00");
        assertThat(byRegion(stats, PHARMACY).get("11110").ratio().value()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("FRACTION 기준으로 바꾸면 같은 입력이 0~1 비율로 적재된다")
    void switchesRatioBasisToFraction() {
        InfraStatPolicy fractionPolicy = new InfraStatPolicy(RatioBasis.FRACTION);

        List<RegionIndustryStat> stats = fractionPolicy.stats(List.of(
                count("11110", RESTAURANT, 75),
                count("11110", PHARMACY, 25)));

        assertThat(byRegion(stats, RESTAURANT).get("11110").ratio().value()).isEqualByComparingTo("0.75");
        assertThat(byRegion(stats, PHARMACY).get("11110").ratio().value()).isEqualByComparingTo("0.25");
    }

    @Test
    @DisplayName("시군구 합계가 0이면 ratio 는 0이다")
    void returnsZeroRatioWhenRegionTotalIsZero() {
        List<RegionIndustryStat> stats = policy.stats(List.of(count("11110", RESTAURANT, 0)));

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).ratio().value()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ score

    @Test
    @DisplayName("score 는 업종별 전국 백분위라 최저 지역이 0, 최고 지역이 100 이다")
    void computesPercentileWithinIndustry() {
        List<RegionIndustryStat> stats = policy.stats(List.of(
                count("11110", RESTAURANT, 10),
                count("11140", RESTAURANT, 20),
                count("11170", RESTAURANT, 30),
                count("11200", RESTAURANT, 40),
                count("11215", RESTAURANT, 50)));

        Map<String, RegionIndustryStat> byRegion = byRegion(stats, RESTAURANT);
        assertThat(byRegion.get("11110").score().value()).isEqualByComparingTo("0.00");
        assertThat(byRegion.get("11140").score().value()).isEqualByComparingTo("25.00");
        assertThat(byRegion.get("11170").score().value()).isEqualByComparingTo("50.00");
        assertThat(byRegion.get("11200").score().value()).isEqualByComparingTo("75.00");
        assertThat(byRegion.get("11215").score().value()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("동점 지역은 같은 점수를 받고, 전 지역이 같으면 0점이 아니라 50점이다")
    void givesMidrankScoreToTies() {
        List<RegionIndustryStat> allTied = policy.stats(List.of(
                count("11110", RESTAURANT, 7),
                count("11140", RESTAURANT, 7),
                count("11170", RESTAURANT, 7)));

        assertThat(allTied).allSatisfy(stat ->
                assertThat(stat.score().value()).isEqualByComparingTo("50.00"));
    }

    @Test
    @DisplayName("표본이 하나면 분포가 없어 50점이다")
    void returnsMiddleScoreForSingleSample() {
        List<RegionIndustryStat> stats = policy.stats(List.of(count("11110", RESTAURANT, 1234)));

        assertThat(stats.get(0).score().value()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("어떤 분포에서도 score 가 [0, 100] 을 벗어나지 않는다")
    void keepsScoreWithinZeroToHundredForAnyDistribution() {
        List<RegionIndustryCount> counts = new java.util.ArrayList<>();
        // 극단적으로 치우친 분포: 한 지역만 매우 크고 나머지는 0
        counts.add(count("11110", RESTAURANT, 2_129_830));
        for (int i = 0; i < 40; i++) {
            counts.add(count(String.format("%05d", 11140 + i), RESTAURANT, 0));
        }

        List<RegionIndustryStat> stats = policy.stats(counts);

        assertThat(stats).allSatisfy(stat -> {
            assertThat(stat.score().value()).isBetween(InfraScore.MIN, InfraScore.MAX);
            assertThat(stat.ratio().value().signum()).isNotNegative();
        });
    }

    @Test
    @DisplayName("업종마다 분포가 따로 계산되어 서로 영향을 주지 않는다")
    void computesDistributionPerIndustry() {
        List<RegionIndustryStat> stats = policy.stats(List.of(
                count("11110", RESTAURANT, 100),
                count("11140", RESTAURANT, 200),
                count("11110", PHARMACY, 5),
                count("11140", PHARMACY, 1)));

        assertThat(byRegion(stats, RESTAURANT).get("11140").score().value()).isEqualByComparingTo("100.00");
        assertThat(byRegion(stats, PHARMACY).get("11110").score().value()).isEqualByComparingTo("100.00");
        assertThat(byRegion(stats, PHARMACY).get("11140").score().value()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ 기타

    @Test
    @DisplayName("같은 시군구·업종이 여러 번 들어오면 합산한다")
    void mergesDuplicateRegionIndustryPairs() {
        List<RegionIndustryStat> stats = policy.stats(List.of(
                count("11110", RESTAURANT, 3),
                count("11110", RESTAURANT, 4)));

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).count()).isEqualTo(7);
    }

    @Test
    @DisplayName("입력이 비어 있으면 빈 결과다")
    void returnsEmptyForEmptyInput() {
        assertThat(policy.stats(List.of())).isEmpty();
        assertThat(policy.stats(null)).isEmpty();
    }

    @Test
    @DisplayName("반올림은 소수 두 자리 HALF_UP 이다")
    void roundsToTwoDecimals() {
        List<RegionIndustryStat> stats = policy.stats(List.of(
                count("11110", RESTAURANT, 1),
                count("11110", PHARMACY, 2)));

        // 1/3 = 33.333...% → 33.33
        assertThat(byRegion(stats, RESTAURANT).get("11110").ratio().value())
                .isEqualByComparingTo(new BigDecimal("33.33"));
        // 2/3 = 66.666...% → 66.67
        assertThat(byRegion(stats, PHARMACY).get("11110").ratio().value())
                .isEqualByComparingTo(new BigDecimal("66.67"));
    }
}
