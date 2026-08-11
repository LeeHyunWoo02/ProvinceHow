package SDD.smash.common.domain.model;

import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidoCodeTest {

    @Test
    @DisplayName("2자리 숫자면 생성된다")
    void createsWhenTwoDigits() {
        assertThat(SidoCode.of("11").value()).isEqualTo("11");
    }

    @ParameterizedTest(name = "[{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", "1", "111", "1A", " 1"})
    @DisplayName("2자리 숫자가 아니면 ADDRESS_CODE_NOT_FOUND 로 거절한다")
    void rejectsInvalidFormat(String value) {
        assertThatThrownBy(() -> new SidoCode(value))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
    }
}
