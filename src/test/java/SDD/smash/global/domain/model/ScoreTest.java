package SDD.smash.global.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreTest {

    @ParameterizedTest(name = "[{index}] value={0}")
    @ValueSource(ints = {0, 1, 50, 99, 100})
    @DisplayName("0~100 이면 생성된다")
    void createsWhenWithinRange(int value) {
        assertThat(Score.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest(name = "[{index}] value={0}")
    @ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("0~100 을 벗어나면 SCORE_OUT_OF_RANGE 로 거절한다")
    void rejectsWhenOutOfRange(int value) {
        assertThatThrownBy(() -> new Score(value))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCORE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("clamped 는 범위를 벗어난 값을 0~100 으로 잘라낸다")
    void clampedTruncatesToRange() {
        assertThat(Score.clamped(-30)).isEqualTo(Score.ZERO);
        assertThat(Score.clamped(130)).isEqualTo(Score.MAX);
        assertThat(Score.clamped(70)).isEqualTo(Score.of(70));
    }

    @Test
    @DisplayName("더한 값이 100 을 넘으면 100 에서 멈춘다")
    void plusCapsAtHundred() {
        assertThat(Score.of(70).plus(Score.of(50))).isEqualTo(Score.MAX);
        assertThat(Score.of(30).plus(Score.of(40))).isEqualTo(Score.of(70));
    }

    @Test
    @DisplayName("ZERO 와 MAX 는 각각 0점, 100점이다")
    void constantsHoldBoundaryValues() {
        assertThat(Score.ZERO.value()).isZero();
        assertThat(Score.MAX.value()).isEqualTo(100);
    }
}
