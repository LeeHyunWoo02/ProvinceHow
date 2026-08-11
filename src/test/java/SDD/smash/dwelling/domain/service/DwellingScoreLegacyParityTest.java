package SDD.smash.dwelling.domain.service;

import SDD.smash.Util.CalculateUtil;
import SDD.smash.common.domain.model.Money;
import SDD.smash.dwelling.domain.model.DwellingType;
import SDD.smash.legacy.dwelling.Service.DwellingScoreSerivce;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이관 안전망. 옛 {@code DwellingScoreSerivce}/{@code CalculateUtil} 과
 * 새 {@code DwellingType}/{@code DwellingScorePolicy}/{@code RentStatCalculator} 가
 * <b>모든 입력에 대해 같은 값</b>을 내는지 격자로 대조한다.
 *
 * <p>추천 API 는 {@code recommendation} 이관(6단계) 전까지 옛 경로를 계속 쓴다.
 * 두 구현이 공존하는 동안 결과가 갈라지면 이 테스트가 먼저 깨진다.
 * 옛 클래스가 삭제되는 6단계에서 이 테스트도 함께 사라진다.
 *
 * <p>옛 메서드가 package-private / private 이라 리플렉션으로 호출한다.
 * 안전망을 위해 운영 코드의 접근 제어를 넓히지는 않는다.
 */
class DwellingScoreLegacyParityTest {

    private static final Class<SDD.smash.legacy.dwelling.Entity.DwellingType> LEGACY_TYPE =
            SDD.smash.legacy.dwelling.Entity.DwellingType.class;

    private final DwellingScoreSerivce legacy = new DwellingScoreSerivce(null, null);
    private final DwellingScorePolicy policy = new DwellingScorePolicy();

    private Object invokeLegacy(String name, Class<?>[] types, Object... args) {
        try {
            Method m = DwellingScoreSerivce.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(legacy, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("옛 구현 호출 실패: " + name, e);
        }
    }

    private int legacyMonthlyScore(Integer median, Integer budget) {
        return (Integer) invokeLegacy("calcMonthlyScore",
                new Class<?>[]{Integer.class, Integer.class}, median, budget);
    }

    private int legacyJeonseScore(Integer median, Integer budget) {
        return (Integer) invokeLegacy("calcJeonseScore",
                new Class<?>[]{Integer.class, Integer.class}, median, budget);
    }

    private Integer legacyValidPrice(SDD.smash.legacy.dwelling.Entity.DwellingType type, Integer price) {
        return (Integer) invokeLegacy("validPrice",
                new Class<?>[]{LEGACY_TYPE, Integer.class}, type, price);
    }

    @Test
    @DisplayName("예산 보정 결과가 옛 구현과 모든 입력에서 같다")
    void normalizeMatchesLegacyForEveryBudget() {
        for (int raw = 0; raw <= 300; raw++) {
            assertThat(DwellingType.MONTHLY.normalize(Money.of(raw)).manwon())
                    .as("MONTHLY budget=%d", raw)
                    .isEqualTo(legacyValidPrice(SDD.smash.legacy.dwelling.Entity.DwellingType.MONTHLY, raw));
        }
        for (int raw = 0; raw <= 30_000; raw += 7) {
            assertThat(DwellingType.JEONSE.normalize(Money.of(raw)).manwon())
                    .as("JEONSE budget=%d", raw)
                    .isEqualTo(legacyValidPrice(SDD.smash.legacy.dwelling.Entity.DwellingType.JEONSE, raw));
        }
    }

    @Test
    @DisplayName("월세 점수가 옛 구현과 모든 조합에서 같다")
    void monthlyScoreMatchesLegacy() {
        // given: 옛 서비스는 이미 보정된 예산을 받는다
        for (int budget = 20; budget <= 110; budget += 10) {
            for (int median = 0; median <= 300; median++) {
                assertThat(policy.score(DwellingType.MONTHLY, Money.of(median), Money.of(budget)).value())
                        .as("MONTHLY median=%d, budget=%d", median, budget)
                        .isEqualTo(legacyMonthlyScore(median, budget));
            }
            assertThat(policy.score(DwellingType.MONTHLY, null, Money.of(budget)).value())
                    .as("MONTHLY median=null, budget=%d", budget)
                    .isEqualTo(legacyMonthlyScore(null, budget));
        }
    }

    @Test
    @DisplayName("전세 점수가 옛 구현과 모든 조합에서 같다")
    void jeonseScoreMatchesLegacy() {
        for (int budget = 3_000; budget <= 21_000; budget += 3_000) {
            for (int median = 0; median <= 60_000; median += 37) {
                assertThat(policy.score(DwellingType.JEONSE, Money.of(median), Money.of(budget)).value())
                        .as("JEONSE median=%d, budget=%d", median, budget)
                        .isEqualTo(legacyJeonseScore(median, budget));
            }
            assertThat(policy.score(DwellingType.JEONSE, null, Money.of(budget)).value())
                    .as("JEONSE median=null, budget=%d", budget)
                    .isEqualTo(legacyJeonseScore(null, budget));
        }
    }

    @Test
    @DisplayName("평균·중앙값이 옛 구현과 같다")
    void rentStatMatchesLegacy() {
        List<List<Integer>> samples = new ArrayList<>(List.of(
                List.of(),
                List.of(7),
                List.of(10, 20),
                List.of(10, 20, 30),
                List.of(10, 20, 30, 40),
                List.of(10, 21, 30, 40),
                List.of(1, 1, 2),
                List.of(5, 3, 9, 1, 7, 7, 2)));
        samples.add(Arrays.asList(10, null, 30));
        samples.add(Arrays.asList(null, null));

        for (List<Integer> sample : samples) {
            assertThat(RentStatCalculator.mean(sample))
                    .as("mean of %s", sample)
                    .isEqualTo(CalculateUtil.mean(sample));
            assertThat(RentStatCalculator.median(sample))
                    .as("median of %s", sample)
                    .isEqualTo(CalculateUtil.median(sample));
        }
        assertThat(RentStatCalculator.mean(null)).isEqualTo(CalculateUtil.mean(null));
        assertThat(RentStatCalculator.median(null)).isEqualTo(CalculateUtil.median(null));
    }
}
