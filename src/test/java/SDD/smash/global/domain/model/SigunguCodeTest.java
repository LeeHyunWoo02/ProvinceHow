package SDD.smash.global.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigunguCodeTest {

    @Test
    @DisplayName("5자리 숫자면 생성된다")
    void createsWhenFiveDigits() {
        SigunguCode code = SigunguCode.of("11110");

        assertThat(code.value()).isEqualTo("11110");
    }

    @ParameterizedTest(name = "[{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", "1111", "111100", "1111A", "  111"})
    @DisplayName("5자리 숫자가 아니면 ADDRESS_CODE_NOT_FOUND 로 거절한다")
    void rejectsInvalidFormat(String value) {
        assertThatThrownBy(() -> new SigunguCode(value))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("앞 2자리를 시도 코드로 잘라낸다")
    void extractsSidoCodeFromFirstTwoDigits() {
        assertThat(SigunguCode.of("41135").sidoCode()).isEqualTo(SidoCode.of("41"));
    }

    @Test
    @DisplayName("값이 같으면 같은 코드다")
    void equalsWhenSameValue() {
        assertThat(SigunguCode.of("11110")).isEqualTo(SigunguCode.of("11110"));
    }
}
