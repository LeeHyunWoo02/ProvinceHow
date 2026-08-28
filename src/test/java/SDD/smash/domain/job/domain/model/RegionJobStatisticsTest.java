package SDD.smash.domain.job.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionJobStatisticsTest {

    private static final RegionJobStatisticsKey KEY = new RegionJobStatisticsKey(
            SigunguCode.of("11110"), JobCode.of("01"), StatisticsMonth.of("2026-07"));

    @Test
    @DisplayName("구인배수는 유효구인인원 / 유효구직자수다")
    void calculatesJobOpeningRatio() {
        RegionJobStatistics statistics = RegionJobStatistics.of(KEY, 100, 200, 50, 200, 1_000);

        assertThat(statistics.jobOpeningRatio()).hasValue(0.2);
    }

    @Test
    @DisplayName("유효구직자수가 0이면 구인배수는 값이 없다")
    void hasNoRatioWhenSeekersAreZero() {
        RegionJobStatistics statistics = RegionJobStatistics.of(KEY, 100, 0, 0, 200, 0);

        assertThat(statistics.jobOpeningRatio()).isEmpty();
    }

    @Test
    @DisplayName("유효구인인원이 0이면 구인배수는 0이다 (값 없음과 구분된다)")
    void ratioIsZeroWhenNoOpenings() {
        RegionJobStatistics statistics = RegionJobStatistics.of(KEY, 0, 10, 0, 0, 500);

        assertThat(statistics.jobOpeningRatio()).hasValue(0.0);
    }

    @Test
    @DisplayName("음수 지표는 DomainException 이다")
    void rejectsNegativeMeasure() {
        assertThatThrownBy(() -> RegionJobStatistics.of(KEY, 1, 1, 1, -1, 1))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.JOB_STATISTICS_INVALID);
    }

    @Test
    @DisplayName("키가 없으면 만들 수 없다")
    void rejectsNullKey() {
        assertThatThrownBy(() -> RegionJobStatistics.of(null, 1, 1, 1, 1, 1))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("키의 구성요소를 그대로 노출한다")
    void exposesKeyComponents() {
        RegionJobStatistics statistics = RegionJobStatistics.reconstitute(KEY, 1, 2, 3, 4, 5);

        assertThat(statistics.sigunguCode()).isEqualTo(SigunguCode.of("11110"));
        assertThat(statistics.jobCode()).isEqualTo(JobCode.of("01"));
        assertThat(statistics.month().text()).isEqualTo("2026-07");
        assertThat(statistics.validSeekers()).isEqualTo(5L);
    }

    @Test
    @DisplayName("키에 기준월이 빠지면 만들 수 없다")
    void rejectsKeyWithoutMonth() {
        assertThatThrownBy(() -> new RegionJobStatisticsKey(SigunguCode.of("11110"), JobCode.of("01"), null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);
    }
}
