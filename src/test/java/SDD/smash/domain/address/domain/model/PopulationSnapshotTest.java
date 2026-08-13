package SDD.smash.domain.address.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PopulationSnapshotTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);

    @Test
    @DisplayName("시군구 코드·인구수·기준월을 함께 갖는다")
    void holdsCodeCountAndStatisticsMonth() {
        // when
        PopulationSnapshot snapshot = PopulationSnapshot.of(SigunguCode.of("11110"), 140_000, JUNE);

        // then
        assertThat(snapshot.sigunguCode()).isEqualTo(SigunguCode.of("11110"));
        assertThat(snapshot.count()).isEqualTo(140_000);
        assertThat(snapshot.statisticsMonth()).isEqualTo(JUNE);
    }

    @Test
    @DisplayName("시군구 코드가 없으면 ADDRESS_CODE_NOT_FOUND 를 던진다")
    void rejectsNullSigunguCode() {
        assertThatThrownBy(() -> PopulationSnapshot.of(null, 100, JUNE))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("기준월이 없으면 NOT_FOUND_YEARMONTH 를 던진다")
    void rejectsNullStatisticsMonth() {
        assertThatThrownBy(() -> PopulationSnapshot.of(SigunguCode.of("11110"), 100, null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);
    }

    @Test
    @DisplayName("인구수가 음수면 VALIDATION_FAILED 를 던진다")
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> PopulationSnapshot.of(SigunguCode.of("11110"), -1, JUNE))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("적재 대상 인구로 바꾸면 기준월은 버려진다")
    void convertsToPopulationDroppingStatisticsMonth() {
        // given
        PopulationSnapshot snapshot = PopulationSnapshot.of(SigunguCode.of("11110"), 140_000, JUNE);

        // when
        Population population = snapshot.toPopulation();

        // then
        assertThat(population).isEqualTo(Population.of(SigunguCode.of("11110"), 140_000));
    }

    @Test
    @DisplayName("같은 코드·인구수·기준월이면 같은 값이다")
    void equalsByValue() {
        assertThat(PopulationSnapshot.of(SigunguCode.of("11110"), 10, JUNE))
                .isEqualTo(PopulationSnapshot.of(SigunguCode.of("11110"), 10, JUNE));
    }
}
