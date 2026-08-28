package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsMonthTest {

    @Test
    @DisplayName("YYYY-MM 문자열을 기준월로 파싱한다")
    void parsesYearMonthText() {
        StatisticsMonth month = StatisticsMonth.of("2026-07");

        assertThat(month.value()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(month.text()).isEqualTo("2026-07");
    }

    @Test
    @DisplayName("앞뒤 공백은 제거하고 파싱한다")
    void trimsSurroundingWhitespace() {
        assertThat(StatisticsMonth.of(" 2023-08 ")).isEqualTo(StatisticsMonth.of(YearMonth.of(2023, 8)));
    }

    @Test
    @DisplayName("자릿수가 모자라거나 형식이 다르면 DomainException 이다")
    void rejectsMalformedText() {
        assertThatThrownBy(() -> StatisticsMonth.of("2026-7"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);

        assertThatThrownBy(() -> StatisticsMonth.of("202607")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> StatisticsMonth.of("")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> StatisticsMonth.of((String) null)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("형식은 맞지만 존재하지 않는 월이면 DomainException 이다")
    void rejectsOutOfRangeMonth() {
        assertThatThrownBy(() -> StatisticsMonth.of("2026-13"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);
    }

    @Test
    @DisplayName("null 기준월로는 만들 수 없다")
    void rejectsNullValue() {
        assertThatThrownBy(() -> new StatisticsMonth(null)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("시간순으로 비교된다")
    void comparesChronologically() {
        assertThat(StatisticsMonth.of("2026-07")).isGreaterThan(StatisticsMonth.of("2025-12"));
    }
}
