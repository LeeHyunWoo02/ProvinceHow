package SDD.smash.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryBackoffTest {

    @Test
    @DisplayName("baseDelayMs가 0 이하이면 0을 반환한다")
    void backoffDelayMsReturnsZeroWhenBaseNonPositive() {
        assertThat(RetryBackoff.backoffDelayMs(0, 2.0, 3, 10_000)).isZero();
        assertThat(RetryBackoff.backoffDelayMs(-100, 2.0, 3, 10_000)).isZero();
    }

    @Test
    @DisplayName("attempt 1은 지수가 0이라 baseDelayMs를 그대로 반환한다")
    void backoffDelayMsFirstAttemptEqualsBase() {
        assertThat(RetryBackoff.backoffDelayMs(500, 2.0, 1, 10_000)).isEqualTo(500);
    }

    @Test
    @DisplayName("attempt가 커질수록 baseDelay * multiplier^(attempt-1)로 증가한다")
    void backoffDelayMsGrowsExponentially() {
        assertThat(RetryBackoff.backoffDelayMs(500, 2.0, 2, 10_000)).isEqualTo(1_000);
        assertThat(RetryBackoff.backoffDelayMs(500, 2.0, 3, 10_000)).isEqualTo(2_000);
        assertThat(RetryBackoff.backoffDelayMs(500, 2.0, 4, 10_000)).isEqualTo(4_000);
    }

    @Test
    @DisplayName("계산된 지연이 maxDelayMs를 넘으면 maxDelayMs로 자른다")
    void backoffDelayMsCapsAtMax() {
        assertThat(RetryBackoff.backoffDelayMs(500, 2.0, 10, 3_000)).isEqualTo(3_000);
    }

    @Test
    @DisplayName("multiplier가 1 미만이면 1.0으로 취급해 지연이 줄지 않는다")
    void backoffDelayMsClampsMultiplierToOne() {
        assertThat(RetryBackoff.backoffDelayMs(500, 0.5, 3, 10_000)).isEqualTo(500);
    }

    @Test
    @DisplayName("Retry-After가 초 단위 정수이면 밀리초로 변환한다")
    void retryAfterMillisParsesSeconds() {
        assertThat(RetryBackoff.retryAfterMillis("3")).isEqualTo(Optional.of(3_000L));
        assertThat(RetryBackoff.retryAfterMillis("  10 ")).isEqualTo(Optional.of(10_000L));
    }

    @Test
    @DisplayName("값이 없거나 공백이면 empty를 반환한다")
    void retryAfterMillisEmptyWhenBlank() {
        assertThat(RetryBackoff.retryAfterMillis(null)).isEmpty();
        assertThat(RetryBackoff.retryAfterMillis("")).isEmpty();
        assertThat(RetryBackoff.retryAfterMillis("   ")).isEmpty();
    }

    @Test
    @DisplayName("숫자가 아닌 값(HTTP-date 등)은 empty로 폴백한다")
    void retryAfterMillisEmptyWhenNonNumeric() {
        assertThat(RetryBackoff.retryAfterMillis("Wed, 21 Oct 2015 07:28:00 GMT")).isEmpty();
    }
}
