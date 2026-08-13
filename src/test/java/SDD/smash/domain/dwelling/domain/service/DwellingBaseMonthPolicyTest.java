package SDD.smash.domain.dwelling.domain.service;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기준월 규칙은 순수 함수이므로 Spring 도 시계도 없이 검증한다.
 */
class DwellingBaseMonthPolicyTest {

    private final DwellingBaseMonthPolicy policy = new DwellingBaseMonthPolicy();

    @Test
    @DisplayName("기본 지연 2개월이면 기준월은 현재월의 2개월 전이다")
    void takesTwoMonthsBackAsConfirmedBaseMonth() {
        YearMonth base = policy.confirmedBaseMonth(YearMonth.of(2026, 8), 2);

        assertThat(base).isEqualTo(YearMonth.of(2026, 6));
    }

    @Test
    @DisplayName("연초에는 기준월이 전년도로 넘어간다")
    void rollsBackAcrossYearBoundary() {
        assertThat(policy.confirmedBaseMonth(YearMonth.of(2026, 1), 2)).isEqualTo(YearMonth.of(2025, 11));
        assertThat(policy.confirmedBaseMonth(YearMonth.of(2026, 2), 2)).isEqualTo(YearMonth.of(2025, 12));
    }

    @Test
    @DisplayName("지연 개월 수를 0으로 주면 현재월을 그대로 기준월로 쓴다")
    void allowsZeroLagMonths() {
        assertThat(policy.confirmedBaseMonth(YearMonth.of(2026, 8), 0)).isEqualTo(YearMonth.of(2026, 8));
    }

    @Test
    @DisplayName("지연 개월 수가 허용 범위를 벗어나면 NOT_FOUND_YEARMONTH 예외")
    void rejectsLagMonthsOutOfRange() {
        assertThatThrownBy(() -> policy.confirmedBaseMonth(YearMonth.of(2026, 8), -1))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);

        assertThatThrownBy(() -> policy.confirmedBaseMonth(YearMonth.of(2026, 8), 13))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("현재월이 없으면 NOT_FOUND_YEARMONTH 예외")
    void rejectsNullCurrentMonth() {
        assertThatThrownBy(() -> policy.confirmedBaseMonth(null, 2))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);
    }

    @Test
    @DisplayName("기준월 후보는 최신 순으로 fallback 개월 수 + 1개가 만들어진다")
    void buildsCandidatesFromNewestToOldest() {
        List<YearMonth> candidates = policy.baseMonthCandidates(YearMonth.of(2026, 8), 2, 3);

        assertThat(candidates).containsExactly(
                YearMonth.of(2026, 6),
                YearMonth.of(2026, 5),
                YearMonth.of(2026, 4),
                YearMonth.of(2026, 3));
    }

    @Test
    @DisplayName("fallback 개월 수가 0이면 후보는 규칙상 기준월 하나뿐이다")
    void buildsSingleCandidateWhenFallbackDisabled() {
        assertThat(policy.baseMonthCandidates(YearMonth.of(2026, 8), 2, 0))
                .containsExactly(YearMonth.of(2026, 6));
    }

    @Test
    @DisplayName("집계 구간은 기준월을 포함해 과거로 12개월이다")
    void buildsTwelveMonthAggregationPeriod() {
        AggregationPeriod period = policy.aggregationPeriod(YearMonth.of(2026, 6), 12);

        assertThat(period.from()).isEqualTo(YearMonth.of(2025, 7));
        assertThat(period.to()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(period.monthCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("override 문자열이 비어 있으면 null 을 돌려 자동 계산으로 넘긴다")
    void treatsBlankOverrideAsAbsent() {
        assertThat(policy.parseOverride(null)).isNull();
        assertThat(policy.parseOverride("")).isNull();
        assertThat(policy.parseOverride("   ")).isNull();
    }

    @Test
    @DisplayName("override 문자열이 yyyyMM 이면 그 연월로 해석한다")
    void parsesOverrideInYearMonthFormat() {
        assertThat(policy.parseOverride("202509")).isEqualTo(YearMonth.of(2025, 9));
        assertThat(policy.parseOverride(" 202601 ")).isEqualTo(YearMonth.of(2026, 1));
    }

    @Test
    @DisplayName("override 형식이 틀리면 조용히 무시하지 않고 NOT_FOUND_YEARMONTH 예외")
    void rejectsMalformedOverride() {
        assertThatThrownBy(() -> policy.parseOverride("2025-09"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);

        assertThatThrownBy(() -> policy.parseOverride("202513"))
                .isInstanceOf(DomainException.class);

        assertThatThrownBy(() -> policy.parseOverride("20250"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("기준월은 yyyyMM 문자열로 표기된다")
    void formatsBaseMonthAsYearMonthText() {
        assertThat(policy.format(YearMonth.of(2026, 1))).isEqualTo("202601");
        assertThat(policy.format(YearMonth.of(2025, 12))).isEqualTo("202512");
    }
}
