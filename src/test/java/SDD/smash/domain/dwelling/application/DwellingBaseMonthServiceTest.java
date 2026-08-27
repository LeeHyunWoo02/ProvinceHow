package SDD.smash.domain.dwelling.application;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.MonthlyRentResult;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.domain.dwelling.domain.port.RentRecordProvider;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시계를 고정해 기준월 자동 계산을 검증한다. 포트는 인메모리 Fake 로 대신한다.
 */
class DwellingBaseMonthServiceTest {

    private static final int LAG = 2;
    private static final int LOOKBACK = 12;
    private static final int MAX_FALLBACK = 3;
    private static final String PROBE_REGION = "11110";

    /** 월별 응답 상태를 지정할 수 있는 Fake 공급자. */
    private static class FakeRentRecordProvider implements RentRecordProvider {
        private final Map<YearMonth, MonthlyRentResult> byMonth = new HashMap<>();
        private final List<YearMonth> probed = new ArrayList<>();
        private final List<HousingType> probedTypes = new ArrayList<>();

        void available(YearMonth ym, int total) {
            byMonth.put(ym, MonthlyRentResult.available(ym, List.of(new RentRecord("A", "1", 10000, 50)), total, 1));
        }

        void confirmedEmpty(YearMonth ym) {
            byMonth.put(ym, MonthlyRentResult.confirmedEmpty(ym, 1));
        }

        void undetermined(YearMonth ym) {
            byMonth.put(ym, MonthlyRentResult.undetermined(ym, 1, "MolitApiException: 게이트웨이 오류"));
        }

        @Override
        public List<RentRecord> fetch(HousingType housingType, SigunguCode code, YearMonth yearMonth) {
            throw new UnsupportedOperationException("탐침은 fetchMonth 만 쓴다");
        }

        @Override
        public MonthlyRentResult fetchMonth(HousingType housingType, SigunguCode code, YearMonth yearMonth) {
            probedTypes.add(housingType);
            probed.add(yearMonth);
            return byMonth.getOrDefault(yearMonth, MonthlyRentResult.confirmedEmpty(yearMonth, 1));
        }
    }

    /** {@code instant} 를 UTC 로 고정한다. 서비스는 내부에서 Asia/Seoul 로 환산한다. */
    private static Clock utcClockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    private DwellingBaseMonthService service(Clock clock, FakeRentRecordProvider provider,
                                             String override, boolean probeEnabled) {
        return new DwellingBaseMonthService(provider, clock, LAG, LOOKBACK, MAX_FALLBACK,
                override, probeEnabled, PROBE_REGION);
    }

    @Test
    @DisplayName("현재 연월은 UTC 가 아니라 Asia/Seoul 기준으로 판정된다")
    void readsCurrentMonthInSeoulZone() {
        // given - UTC 2026-02-28 15:30 == KST 2026-03-01 00:30
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        DwellingBaseMonthService service =
                service(utcClockAt("2026-02-28T15:30:00Z"), provider, "", false);

        // when / then
        assertThat(service.currentMonth()).isEqualTo(YearMonth.of(2026, 3));
    }

    @Test
    @DisplayName("override 가 없으면 현재월의 2개월 전이 기준월이 된다")
    void resolvesBaseMonthTwoMonthsBeforeCurrent() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        provider.available(YearMonth.of(2026, 6), 181);
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "", true);

        // when / then
        assertThat(service.resolveBaseMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(service.resolveBaseMonthText()).isEqualTo("202606");
    }

    @Test
    @DisplayName("탐침은 거래량이 가장 많은 아파트로 고정한다")
    void probesWithApartmentHousingType() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        provider.available(YearMonth.of(2026, 6), 181);
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "", true);

        // when
        service.resolveBaseMonth();

        // then
        assertThat(provider.probedTypes).containsExactly(HousingType.APARTMENT);
    }

    @Test
    @DisplayName("월말 마지막 순간에도 기준월이 다음 달로 넘어가지 않는다")
    void keepsBaseMonthAtMonthEndBoundary() {
        // given - KST 2026-03-31 23:59
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        provider.available(YearMonth.of(2026, 1), 100);
        DwellingBaseMonthService service =
                service(utcClockAt("2026-03-31T14:59:59Z"), provider, "", true);

        // when / then
        assertThat(service.resolveBaseMonth()).isEqualTo(YearMonth.of(2026, 1));
    }

    @Test
    @DisplayName("연초에는 기준월이 전년도로 넘어간다")
    void resolvesBaseMonthAcrossYearBoundary() {
        // given - KST 2026-01-01 09:00
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        provider.available(YearMonth.of(2025, 11), 120);
        DwellingBaseMonthService service =
                service(utcClockAt("2026-01-01T00:00:00Z"), provider, "", true);

        // when / then
        assertThat(service.resolveBaseMonthText()).isEqualTo("202511");
    }

    @Test
    @DisplayName("override 가 지정되면 자동 계산과 탐침을 모두 건너뛴다")
    void appliesConfiguredOverrideWithoutProbing() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "202509", true);

        // when
        YearMonth base = service.resolveBaseMonth();

        // then
        assertThat(base).isEqualTo(YearMonth.of(2025, 9));
        assertThat(provider.probed).isEmpty();
    }

    @Test
    @DisplayName("override 형식이 틀리면 자동 계산으로 넘어가지 않고 예외를 던진다")
    void failsFastOnMalformedOverride() {
        // given
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), new FakeRentRecordProvider(), "2025-09", true);

        // when / then
        assertThatThrownBy(service::resolveBaseMonth)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND_YEARMONTH);
    }

    @Test
    @DisplayName("규칙상 기준월이 확정 0건이면 직전 월로 물러난다")
    void fallsBackToPreviousMonthWhenConfirmedEmpty() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        provider.confirmedEmpty(YearMonth.of(2026, 6));
        provider.available(YearMonth.of(2026, 5), 155);
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "", true);

        // when
        YearMonth base = service.resolveBaseMonth();

        // then
        assertThat(base).isEqualTo(YearMonth.of(2026, 5));
        assertThat(provider.probed).containsExactly(YearMonth.of(2026, 6), YearMonth.of(2026, 5));
    }

    @Test
    @DisplayName("탐침이 실패하면 장애를 자료 없음으로 오해하지 않고 규칙상 기준월을 유지한다")
    void keepsPrimaryBaseMonthWhenProbeFails() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        provider.undetermined(YearMonth.of(2026, 6));
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "", true);

        // when
        YearMonth base = service.resolveBaseMonth();

        // then
        assertThat(base).isEqualTo(YearMonth.of(2026, 6));
        assertThat(provider.probed).containsExactly(YearMonth.of(2026, 6));
    }

    @Test
    @DisplayName("후보가 모두 0건이면 무한히 과거로 가지 않고 규칙상 기준월을 유지한다")
    void stopsFallbackAfterMaxCandidates() {
        // given - 모든 달이 확정 0건
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "", true);

        // when
        YearMonth base = service.resolveBaseMonth();

        // then
        assertThat(base).isEqualTo(YearMonth.of(2026, 6));
        assertThat(provider.probed).hasSize(MAX_FALLBACK + 1);
    }

    @Test
    @DisplayName("탐침을 끄면 외부 호출 없이 규칙상 기준월을 쓴다")
    void skipsProbeWhenDisabled() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "", false);

        // when
        YearMonth base = service.resolveBaseMonth();

        // then
        assertThat(base).isEqualTo(YearMonth.of(2026, 6));
        assertThat(provider.probed).isEmpty();
    }

    @Test
    @DisplayName("집계 구간은 기준월을 포함해 최근 12개월이다")
    void resolvesTwelveMonthAggregationPeriod() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        provider.available(YearMonth.of(2026, 6), 181);
        DwellingBaseMonthService service =
                service(utcClockAt("2026-08-13T02:00:00Z"), provider, "", true);

        // when
        AggregationPeriod period = service.resolveAggregationPeriod();

        // then
        assertThat(period.from()).isEqualTo(YearMonth.of(2025, 7));
        assertThat(period.to()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(service.lookbackMonths()).isEqualTo(LOOKBACK);
    }

    @Test
    @DisplayName("탐침 지역 코드가 유효하지 않으면 탐침 없이 규칙상 기준월을 쓴다")
    void skipsProbeWhenProbeRegionIsInvalid() {
        // given
        FakeRentRecordProvider provider = new FakeRentRecordProvider();
        DwellingBaseMonthService service = new DwellingBaseMonthService(
                provider, utcClockAt("2026-08-13T02:00:00Z"), LAG, LOOKBACK, MAX_FALLBACK,
                "", true, "abc");

        // when
        YearMonth base = service.resolveBaseMonth();

        // then
        assertThat(base).isEqualTo(YearMonth.of(2026, 6));
        assertThat(provider.probed).isEmpty();
    }
}
