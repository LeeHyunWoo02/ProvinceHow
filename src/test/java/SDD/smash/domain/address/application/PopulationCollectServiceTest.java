package SDD.smash.domain.address.application;

import SDD.smash.domain.address.application.dto.PopulationCollectionInfo;
import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.domain.port.PopulationSnapshotProvider;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PopulationCollectServiceTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth MAY = YearMonth.of(2026, 5);

    @Mock
    PopulationSnapshotProvider populationSnapshotProvider;

    @Mock
    AddressQueryService addressQueryService;

    @InjectMocks
    PopulationCollectService populationCollectService;

    @Test
    @DisplayName("공급자 설정이 없으면 외부 호출 없이 건너뛴다")
    void skipsWithoutCallingProviderWhenUnavailable() {
        // given
        given(populationSnapshotProvider.isAvailable()).willReturn(false);

        // when
        PopulationCollectionInfo info = populationCollectService.collect(JUNE);

        // then
        assertThat(info.skipped()).isTrue();
        assertThat(info.snapshots()).isEmpty();
        then(populationSnapshotProvider).should(never()).fetchLatestNotAfter(any());
        then(addressQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기준월이 없으면 수집하지 않는다")
    void skipsWhenRequestedMonthIsMissing() {
        // when
        PopulationCollectionInfo info = populationCollectService.collect(null);

        // then
        assertThat(info.skipped()).isTrue();
        then(populationSnapshotProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("sigungu 테이블에 없는 코드는 임의 매핑 없이 제외하고 건수만 남긴다")
    void excludesCodesMissingFromSigunguTable() {
        // given
        given(populationSnapshotProvider.isAvailable()).willReturn(true);
        given(populationSnapshotProvider.fetchLatestNotAfter(JUNE)).willReturn(List.of(
                snapshot("11110", 140_000, JUNE),
                snapshot("99999", 30_000, JUNE),
                snapshot("11140", 120_000, JUNE)));
        given(addressQueryService.getAllSigunguCodes())
                .willReturn(List.of(SigunguCode.of("11110"), SigunguCode.of("11140")));

        // when
        PopulationCollectionInfo info = populationCollectService.collect(JUNE);

        // then
        assertThat(info.fetchedCount()).isEqualTo(3);
        assertThat(info.loadedCount()).isEqualTo(2);
        assertThat(info.unmatchedCodes()).containsExactly("99999");
        assertThat(info.snapshots()).extracting(s -> s.sigunguCode().value())
                .containsExactly("11110", "11140");
    }

    @Test
    @DisplayName("확보한 자료의 기준월을 결과에 담는다 - 요청 월과 다를 수 있다")
    void reportsResolvedStatisticsMonthWhichMayDifferFromRequest() {
        // given - 6월 자료가 아직 없어 공급자가 5월로 내려간 상황
        given(populationSnapshotProvider.isAvailable()).willReturn(true);
        given(populationSnapshotProvider.fetchLatestNotAfter(JUNE))
                .willReturn(List.of(snapshot("11110", 139_000, MAY)));
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(SigunguCode.of("11110")));

        // when
        PopulationCollectionInfo info = populationCollectService.collect(JUNE);

        // then
        assertThat(info.requestedMonth()).isEqualTo(JUNE);
        assertThat(info.statisticsMonth()).isEqualTo(MAY);
        assertThat(info.loadedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("확정 자료가 없으면 빈 결과를 돌려주고 테이블을 조회하지 않는다")
    void returnsEmptyWhenProviderHasNoData() {
        // given
        given(populationSnapshotProvider.isAvailable()).willReturn(true);
        given(populationSnapshotProvider.fetchLatestNotAfter(JUNE)).willReturn(List.of());

        // when
        PopulationCollectionInfo info = populationCollectService.collect(JUNE);

        // then
        assertThat(info.skipped()).isFalse();
        assertThat(info.statisticsMonth()).isNull();
        assertThat(info.snapshots()).isEmpty();
        then(addressQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공급자 실패는 삼키지 않고 그대로 올린다")
    void rethrowsProviderFailure() {
        // given
        given(populationSnapshotProvider.isAvailable()).willReturn(true);
        willThrow(new IllegalStateException("boom"))
                .given(populationSnapshotProvider).fetchLatestNotAfter(JUNE);

        // when / then
        assertThatThrownBy(() -> populationCollectService.collect(JUNE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("같은 기준월로 다시 수집하면 같은 적재 대상이 나온다")
    void producesSameResultOnRerunWithSameBaseMonth() {
        // given
        given(populationSnapshotProvider.isAvailable()).willReturn(true);
        given(populationSnapshotProvider.fetchLatestNotAfter(JUNE))
                .willReturn(List.of(snapshot("11110", 140_000, JUNE)));
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(SigunguCode.of("11110")));

        // when
        PopulationCollectionInfo first = populationCollectService.collect(JUNE);
        PopulationCollectionInfo second = populationCollectService.collect(JUNE);

        // then
        assertThat(second).isEqualTo(first);
    }

    private static PopulationSnapshot snapshot(String code, int count, YearMonth month) {
        return PopulationSnapshot.of(SigunguCode.of(code), count, month);
    }
}
