package SDD.smash.domain.address.infrastructure.external;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.infrastructure.external.dto.KosisPopulationRow;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KosisPopulationJsonMapperTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"00", "11", "1111051", "111", ""})
    @DisplayName("5자리가 아닌 분류값은 시군구 코드가 아니다")
    void rejectsCodesThatAreNotFiveDigits(String raw) {
        assertThat(KosisPopulationJsonMapper.normalizeSigunguCode(raw)).isNull();
    }

    @Test
    @DisplayName("BOM·공백이 섞인 5자리 코드를 정규화한다")
    void normalizesFiveDigitCodeWithInvisibleCharacters() {
        // BOM + 앞뒤 공백
        assertThat(KosisPopulationJsonMapper.normalizeSigunguCode("\uFEFF 11110 ")).isEqualTo("11110");
    }

    @Test
    @DisplayName("콤마가 섞인 수치를 정수로 읽는다")
    void parsesCountWithThousandSeparator() {
        assertThat(KosisPopulationJsonMapper.parseCount("1,234,567")).isEqualTo(1_234_567);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"-", "…", "X", ""})
    @DisplayName("통계부호나 빈 값은 수치로 읽지 않는다")
    void rejectsStatisticalSymbols(String raw) {
        assertThat(KosisPopulationJsonMapper.parseCount(raw)).isNull();
    }

    @Test
    @DisplayName("yyyyMM 수록시점을 기준월로 읽고 그 외 형식은 버린다")
    void parsesMonthlyPeriodOnly() {
        assertThat(KosisPopulationJsonMapper.parseMonth("202606")).isEqualTo(YearMonth.of(2026, 6));
        assertThat(KosisPopulationJsonMapper.parseMonth("2026")).isNull();
        assertThat(KosisPopulationJsonMapper.parseMonth("20260601")).isNull();
    }

    @Test
    @DisplayName("시군구 행은 인구 스냅샷으로 승격된다")
    void convertsSigunguRowToSnapshot() {
        // given
        KosisPopulationRow row = new KosisPopulationRow("11110", "종로구", "T20", "202606", "140,000");

        // when
        Optional<PopulationSnapshot> snapshot = KosisPopulationJsonMapper.toSnapshot(row);

        // then
        assertThat(snapshot).contains(
                PopulationSnapshot.of(SigunguCode.of("11110"), 140_000, YearMonth.of(2026, 6)));
    }

    @Test
    @DisplayName("시군구가 아니거나 값을 읽을 수 없으면 예외 대신 빈 값을 돌려준다")
    void returnsEmptyInsteadOfThrowing() {
        assertThat(KosisPopulationJsonMapper.toSnapshot(null)).isEmpty();
        assertThat(KosisPopulationJsonMapper.toSnapshot(
                new KosisPopulationRow("11", "서울특별시", "T20", "202606", "9,300,000"))).isEmpty();
        assertThat(KosisPopulationJsonMapper.toSnapshot(
                new KosisPopulationRow("11110", "종로구", "T20", "202606", "-"))).isEmpty();
        assertThat(KosisPopulationJsonMapper.toSnapshot(
                new KosisPopulationRow("11110", "종로구", "T20", "2026", "140000"))).isEmpty();
    }
}
