package SDD.smash.common.domain.model;

import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @ParameterizedTest(name = "[{index}] manwon={0}")
    @ValueSource(ints = {0, 20, 110, 21_000})
    @DisplayName("0 이상이면 생성된다")
    void createsWhenNotNegative(int manwon) {
        assertThat(Money.of(manwon).manwon()).isEqualTo(manwon);
    }

    @ParameterizedTest(name = "[{index}] manwon={0}")
    @ValueSource(ints = {-1, -21_000})
    @DisplayName("음수면 PRICE_AMOUNT_NOT_VALID 로 거절한다")
    void rejectsNegativeAmount(int manwon) {
        assertThatThrownBy(() -> new Money(manwon))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRICE_AMOUNT_NOT_VALID);
    }

    @Test
    @DisplayName("차이는 순서와 무관하게 절대값이다")
    void diffToReturnsAbsoluteValue() {
        assertThat(Money.of(80).diffTo(Money.of(60))).isEqualTo(20);
        assertThat(Money.of(60).diffTo(Money.of(80))).isEqualTo(20);
        assertThat(Money.of(60).diffTo(Money.of(60))).isZero();
    }

    @Test
    @DisplayName("isAtLeast 는 같거나 클 때 참이다")
    void isAtLeastWhenEqualOrGreater() {
        assertThat(Money.of(110).isAtLeast(Money.of(110))).isTrue();
        assertThat(Money.of(120).isAtLeast(Money.of(110))).isTrue();
        assertThat(Money.of(100).isAtLeast(Money.of(110))).isFalse();
    }

    @Test
    @DisplayName("ZERO 는 0만원이다")
    void zeroConstantHoldsNoAmount() {
        assertThat(Money.ZERO.manwon()).isZero();
        assertThat(Money.ZERO.isZero()).isTrue();
    }
}
