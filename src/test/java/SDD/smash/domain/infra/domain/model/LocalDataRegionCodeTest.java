package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDataRegionCodeTest {

    @Test
    @DisplayName("7자리 숫자 코드를 받는다")
    void acceptsSevenDigitCode() {
        assertThat(LocalDataRegionCode.of("3000000").value()).isEqualTo("3000000");
        assertThat(LocalDataRegionCode.of(" 4181000 ").value()).isEqualTo("4181000");
    }

    @Test
    @DisplayName("시도 전체를 뜻하는 _ALL 접미 코드도 허용한다")
    void acceptsAggregateCode() {
        LocalDataRegionCode code = LocalDataRegionCode.of("6110000_ALL");
        assertThat(code.isAggregate()).isTrue();
        assertThat(LocalDataRegionCode.of("6110000").isAggregate()).isFalse();
    }

    @Test
    @DisplayName("자릿수가 다르거나 숫자가 아니면 ADDRESS_CODE_NOT_FOUND 로 거부된다")
    void rejectsMalformedCode() {
        assertThatThrownBy(() -> LocalDataRegionCode.of("11110"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);

        assertThatThrownBy(() -> LocalDataRegionCode.of("30000A0")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> LocalDataRegionCode.of(null)).isInstanceOf(DomainException.class);
    }
}
