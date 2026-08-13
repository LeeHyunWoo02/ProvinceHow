package SDD.smash.domain.dwelling.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationPeriodTest {

    @Test
    @DisplayName("기준월에서 과거로 N개월(기준월 포함) 구간이 만들어진다")
    void buildsPeriodEndingAtBaseMonth() {
        AggregationPeriod period = AggregationPeriod.endingAt(YearMonth.of(2026, 6), 12);

        assertThat(period.from()).isEqualTo(YearMonth.of(2025, 7));
        assertThat(period.to()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(period.months()).hasSize(12).startsWith(YearMonth.of(2025, 7));
    }

    @Test
    @DisplayName("1개월 구간은 시작월과 기준월이 같다")
    void buildsSingleMonthPeriod() {
        AggregationPeriod period = AggregationPeriod.endingAt(YearMonth.of(2026, 6), 1);

        assertThat(period.from()).isEqualTo(period.to());
        assertThat(period.monthCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("집계 개월 수가 0 이하거나 24 초과면 NOT_FOUND_YEARMONTH 예외")
    void rejectsMonthCountOutOfRange() {
        assertThatThrownBy(() -> AggregationPeriod.endingAt(YearMonth.of(2026, 6), 0))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);

        assertThatThrownBy(() -> AggregationPeriod.endingAt(YearMonth.of(2026, 6), 25))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("시작월이 기준월보다 뒤면 생성되지 않는다")
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> new AggregationPeriod(YearMonth.of(2026, 7), YearMonth.of(2026, 6)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);
    }

    @Test
    @DisplayName("연말을 넘는 구간도 월 목록이 연속한다")
    void listsMonthsAcrossYearBoundary() {
        AggregationPeriod period = AggregationPeriod.endingAt(YearMonth.of(2026, 2), 4);

        assertThat(period.months()).containsExactly(
                YearMonth.of(2025, 11), YearMonth.of(2025, 12),
                YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }

    @Test
    @DisplayName("구간 포함 여부는 양끝을 포함해 판정한다")
    void containsBoundaryMonths() {
        AggregationPeriod period = AggregationPeriod.endingAt(YearMonth.of(2026, 6), 3);

        assertThat(period.contains(YearMonth.of(2026, 4))).isTrue();
        assertThat(period.contains(YearMonth.of(2026, 6))).isTrue();
        assertThat(period.contains(YearMonth.of(2026, 3))).isFalse();
        assertThat(period.contains(YearMonth.of(2026, 7))).isFalse();
    }
}
