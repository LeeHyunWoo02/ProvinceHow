package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfraScoreTest {

    @Test
    @DisplayName("0 과 100 은 경계값으로 허용된다")
    void acceptsBoundaryValues() {
        assertThat(InfraScore.of(BigDecimal.ZERO).value()).isEqualByComparingTo("0.00");
        assertThat(InfraScore.of(BigDecimal.valueOf(100)).value()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("100 을 넘으면 SCORE_OUT_OF_RANGE 로 막힌다 - 적재 전에 걸러야 추천 API 가 400 으로 터지지 않는다")
    void rejectsValueAboveHundred() {
        assertThatThrownBy(() -> InfraScore.of(BigDecimal.valueOf(100.01)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCORE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("음수면 SCORE_OUT_OF_RANGE 로 막힌다")
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> InfraScore.of(BigDecimal.valueOf(-0.01)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCORE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("소수 셋째 자리는 HALF_UP 으로 두 자리까지 반올림된다")
    void roundsToTwoDecimalsHalfUp() {
        assertThat(InfraScore.of(BigDecimal.valueOf(12.345)).value()).isEqualByComparingTo("12.35");
        assertThat(InfraScore.of(BigDecimal.valueOf(12.344)).value()).isEqualByComparingTo("12.34");
    }

    @Test
    @DisplayName("반올림해서 100.00 이 되는 값(100.004)은 허용되고 100.005 는 막힌다")
    void appliesRangeCheckAfterRounding() {
        assertThat(InfraScore.of(BigDecimal.valueOf(100.004)).value()).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> InfraScore.of(BigDecimal.valueOf(100.005)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("null 이거나 유한하지 않은 수는 거부된다")
    void rejectsNullAndNonFiniteValues() {
        assertThatThrownBy(() -> InfraScore.of((BigDecimal) null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> InfraScore.of(Double.NaN)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> InfraScore.of(Double.POSITIVE_INFINITY)).isInstanceOf(DomainException.class);
    }
}
