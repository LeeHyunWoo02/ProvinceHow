package SDD.smash.domain.dwelling.infrastructure.batch;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.RentCollection;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.domain.dwelling.domain.port.RentRecordProvider;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.DwellingByTypeUpsertRow;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.DwellingUpsertBundle;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.WorkItem;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3종 수집 · 풀링 통합 계산 · (시군구, 유형) 단위 부분 실패 격리를 검증한다.
 * 외부 공급자는 인메모리 Fake 로 대체한다 — 유형마다 다른 결과를 주는 시나리오가 많아 Fake 가 읽기 쉽다.
 */
class DwellingUpsertProcessorTest {

    private static final SigunguCode SIGUNGU = SigunguCode.of("46110");
    private static final YearMonth FROM = YearMonth.of(2026, 1);
    private static final YearMonth TO = YearMonth.of(2026, 3);

    private final FakeRentRecordProvider provider = new FakeRentRecordProvider();
    private final DwellingUpsertProcessor processor = new DwellingUpsertProcessor(provider);

    @Test
    @DisplayName("3종 모두 정상이면 통합 1행과 유형별 3행을 만든다")
    void producesCombinedRowAndRowPerHousingTypeWhenAllTypesSucceed() {
        // given
        provider.success(HousingType.APARTMENT, monthly(50), jeonse(20_000));
        provider.success(HousingType.MULTIPLEX_HOUSE, monthly(30), jeonse(10_000));
        provider.success(HousingType.DETACHED_HOUSE, monthly(40), jeonse(12_000));

        // when
        DwellingUpsertBundle bundle = processor.process(workItem());

        // then
        assertThat(bundle).isNotNull();
        assertThat(bundle.combined().getSigunguCode()).isEqualTo("46110");
        assertThat(bundle.combined().getMonthAvg()).isEqualTo(40.0);
        assertThat(bundle.combined().getJeonseAvg()).isEqualTo(14_000.0);
        assertThat(bundle.byType()).hasSize(3);
        assertThat(bundle.byType()).extracting(DwellingByTypeUpsertRow::getHousingType)
                .containsExactly("APARTMENT", "MULTIPLEX_HOUSE", "DETACHED_HOUSE");
    }

    @Test
    @DisplayName("아파트에만 실거래가 있으면 통합값이 아파트만으로 계산되고 유형별은 1행이다")
    void aggregatesOnlyApartmentWhenOtherTypesHaveNoDeal() {
        // given — 전남 재현 시나리오. 응답은 정상이지만 거래가 0건이다
        provider.success(HousingType.APARTMENT, monthly(50), jeonse(20_000));
        provider.success(HousingType.MULTIPLEX_HOUSE, List.of());
        provider.success(HousingType.DETACHED_HOUSE, List.of());

        // when
        DwellingUpsertBundle bundle = processor.process(workItem());

        // then
        assertThat(bundle).isNotNull();
        assertThat(bundle.combined().getMonthAvg()).isEqualTo(50.0);
        assertThat(bundle.combined().getJeonseMid()).isEqualTo(20_000);
        assertThat(bundle.byType()).hasSize(1);
        assertThat(bundle.byType().get(0).getHousingType()).isEqualTo("APARTMENT");
    }

    @Test
    @DisplayName("한 유형이 부분 실패하면 그 유형만 빠지고 나머지 유형과 통합값은 정상 적재된다")
    void isolatesPartialFailureToTheFailedHousingTypeOnly() {
        // given — 연립다세대만 수집 실패한 달이 있다
        provider.success(HousingType.APARTMENT, monthly(50));
        provider.partiallyFailed(HousingType.MULTIPLEX_HOUSE, monthly(1_000));
        provider.success(HousingType.DETACHED_HOUSE, monthly(30));

        // when
        DwellingUpsertBundle bundle = processor.process(workItem());

        // then — 실패한 유형의 레코드는 통합 평균에도 섞이지 않는다
        assertThat(bundle).isNotNull();
        assertThat(bundle.combined().getMonthAvg()).isEqualTo(40.0);
        assertThat(bundle.byType()).extracting(DwellingByTypeUpsertRow::getHousingType)
                .containsExactly("APARTMENT", "DETACHED_HOUSE");
    }

    @Test
    @DisplayName("3종 모두 데이터가 없으면 null 을 반환해 건너뛴다")
    void returnsNullWhenAllHousingTypesAreEmpty() {
        // given
        for (HousingType housingType : HousingType.values()) {
            provider.success(housingType, List.of());
        }

        // when
        DwellingUpsertBundle bundle = processor.process(workItem());

        // then
        assertThat(bundle).isNull();
    }

    @Test
    @DisplayName("통합 중앙값은 유형별 중앙값들의 중앙값이 아니라 풀링한 원시값의 중앙값이다")
    void computesCombinedMedianFromPooledRawRecordsNotFromMedianOfMedians() {
        // given — 유형별 중앙값은 15 / 100 / 200 이라 '중앙값들의 중앙값'은 100 이 된다
        provider.success(HousingType.APARTMENT, monthly(10, 20));
        provider.success(HousingType.MULTIPLEX_HOUSE, monthly(100));
        provider.success(HousingType.DETACHED_HOUSE, monthly(200));

        // when
        DwellingUpsertBundle bundle = processor.process(workItem());

        // then — 풀링하면 [10, 20, 100, 200] 이므로 중앙값은 60 이다
        assertThat(bundle).isNotNull();
        assertThat(bundle.combined().getMonthMid()).isEqualTo(60);
        assertThat(bundle.byType()).extracting(DwellingByTypeUpsertRow::getMonthMid)
                .containsExactly(15, 100, 200);
    }

    private WorkItem workItem() {
        return new WorkItem(SIGUNGU, FROM, TO);
    }

    private static List<RentRecord> monthly(int... monthlyRents) {
        List<RentRecord> records = new ArrayList<>();
        for (int rent : monthlyRents) {
            records.add(new RentRecord("테스트", "1-1", 1_000, rent));
        }
        return records;
    }

    private static List<RentRecord> jeonse(int... deposits) {
        List<RentRecord> records = new ArrayList<>();
        for (int deposit : deposits) {
            records.add(new RentRecord("테스트", "1-1", deposit, 0));
        }
        return records;
    }

    @SafeVarargs
    private static List<RentRecord> concat(List<RentRecord>... lists) {
        List<RentRecord> all = new ArrayList<>();
        for (List<RentRecord> list : lists) {
            all.addAll(list);
        }
        return all;
    }

    /** 유형별로 미리 정해둔 수집 결과를 돌려주는 Fake. */
    private static class FakeRentRecordProvider implements RentRecordProvider {

        private final Map<HousingType, RentCollection> results = new EnumMap<>(HousingType.class);

        @SafeVarargs
        private void success(HousingType housingType, List<RentRecord>... records) {
            put(housingType, concat(records), List.of());
        }

        @SafeVarargs
        private void partiallyFailed(HousingType housingType, List<RentRecord>... records) {
            put(housingType, concat(records), List.of(FROM));
        }

        private void put(HousingType housingType, List<RentRecord> records, List<YearMonth> failedMonths) {
            AggregationPeriod period = new AggregationPeriod(FROM, TO);
            results.put(housingType, new RentCollection(SIGUNGU, period, records,
                    period.monthCount(), failedMonths, List.of()));
        }

        @Override
        public List<RentRecord> fetch(HousingType housingType, SigunguCode code, YearMonth yearMonth) {
            throw new UnsupportedOperationException("배치는 collect 만 쓴다");
        }

        @Override
        public RentCollection collect(HousingType housingType, SigunguCode code, AggregationPeriod period) {
            return results.get(housingType);
        }
    }
}
