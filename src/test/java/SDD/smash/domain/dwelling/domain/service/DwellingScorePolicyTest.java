package SDD.smash.domain.dwelling.domain.service;

import SDD.smash.domain.dwelling.domain.model.DwellingType;
import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.Score;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주거 적합도 점수 정책. 순수 함수라 모킹이 없다.
 *
 * <p>계약: {@code score} 는 <b>이미 {@link DwellingType#normalize} 로 보정된 예산</b>을 받는다.
 * 따라서 아래 테스트의 budget 은 전부 유형별 단위(월세 10 / 전세 3000)에 맞춘 값이다.
 */
class DwellingScorePolicyTest {

    private final DwellingScorePolicy policy = new DwellingScorePolicy();

    @Test
    @DisplayName("월세 중앙값이 예산과 같으면 감점 없이 100점")
    void scoresFullWhenMonthlyMedianEqualsBudget() {
        Score score = policy.score(DwellingType.MONTHLY, Money.of(60), Money.of(60));

        assertThat(score).isEqualTo(Score.of(100));
    }

    @Test
    @DisplayName("월세는 10만원 차이마다 10점씩 감점된다")
    void deductsTenPointsPerMonthlyStep() {
        // 20만원 차이 = 2단위 = 20점 감점
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(80), Money.of(60)))
                .isEqualTo(Score.of(80));
        // 예산보다 싼 쪽도 대칭으로 감점된다(차이는 절댓값)
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(40), Money.of(60)))
                .isEqualTo(Score.of(80));
    }

    @Test
    @DisplayName("한 단위 미만의 차이는 정수 나눗셈으로 버려져 감점되지 않는다")
    void ignoresSubStepDifference() {
        // 5만원 차이 = 0단위(5/10=0) = 감점 없음
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(65), Money.of(60)))
                .isEqualTo(Score.of(100));
    }

    @Test
    @DisplayName("감점이 100점을 넘겨도 0점 미만으로 내려가지 않는다")
    void clampsToZeroWhenPenaltyExceedsHundred() {
        // 480만원 차이 = 48단위 = 480점 감점 → 0 으로 클램프
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(500), Money.of(20)))
                .isEqualTo(Score.ZERO);
    }

    @Test
    @DisplayName("실거래 중앙값이 없으면(null) 무득점 0점")
    void scoresZeroWhenMedianIsNull() {
        assertThat(policy.score(DwellingType.MONTHLY, null, Money.of(60)))
                .isEqualTo(Score.ZERO);
    }

    @Test
    @DisplayName("예산이 상한이고 시세가 그 이상이면 만점이다")
    void scoresMaxWhenBudgetIsUpperBoundAndMedianAtLeastBudget() {
        // 월세 상한 110 을 고르고 시세가 150 → 가격을 따지지 않겠다는 뜻이므로 만점
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(150), Money.of(110)))
                .isEqualTo(Score.MAX);
        // 상한이고 시세가 정확히 상한이어도 만점
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(110), Money.of(110)))
                .isEqualTo(Score.MAX);
    }

    @Test
    @DisplayName("예산이 상한이어도 시세가 상한보다 싸면 만점이 아니라 감점식을 탄다")
    void doesNotShortCircuitToMaxWhenMedianBelowUpperBound() {
        // 상한 110, 시세 80 → 만점 분기 대신 diff 30 = 3단위 = 30점 감점 → 70
        assertThat(policy.score(DwellingType.MONTHLY, Money.of(80), Money.of(110)))
                .isEqualTo(Score.of(70));
    }

    @Test
    @DisplayName("전세는 3000만원 차이마다 10점씩 감점된다")
    void deductsTenPointsPerJeonseStep() {
        // 3000만원 차이 = 1단위 = 10점 감점
        assertThat(policy.score(DwellingType.JEONSE, Money.of(12_000), Money.of(9_000)))
                .isEqualTo(Score.of(90));
    }

    @Test
    @DisplayName("전세 상한(21000)을 고르고 시세가 그 이상이면 만점이다")
    void scoresMaxForJeonseUpperBound() {
        assertThat(policy.score(DwellingType.JEONSE, Money.of(25_000), Money.of(21_000)))
                .isEqualTo(Score.MAX);
    }
}
