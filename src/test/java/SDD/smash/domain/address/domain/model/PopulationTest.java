package SDD.smash.domain.address.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PopulationTest {

    @Test
    @DisplayName("시군구 코드와 인구수를 함께 갖는다")
    void holdsCodeAndCount() {
        // when
        Population population = Population.of(SigunguCode.of("11110"), 140_000);

        // then
        assertThat(population.sigunguCode()).isEqualTo(SigunguCode.of("11110"));
        assertThat(population.count()).isEqualTo(140_000);
    }

    @Test
    @DisplayName("시군구 코드가 없으면 ADDRESS_CODE_NOT_FOUND 를 던진다")
    void rejectsNullSigunguCode() {
        assertThatThrownBy(() -> Population.of(null, 100))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("인구수가 음수면 VALIDATION_FAILED 를 던진다")
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> Population.of(SigunguCode.of("11110"), -1))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("같은 코드·인구수면 같은 값이다")
    void equalsByValue() {
        assertThat(Population.of(SigunguCode.of("11110"), 10))
                .isEqualTo(Population.of(SigunguCode.of("11110"), 10));
    }
}
