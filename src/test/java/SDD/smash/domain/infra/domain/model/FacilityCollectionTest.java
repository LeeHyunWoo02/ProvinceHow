package SDD.smash.domain.infra.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityCollectionTest {

    private static final LocalDataRegionCode JONGNO = LocalDataRegionCode.of("3000000");

    private static InfraFacility facility(String managementNo, BusinessStatus status) {
        return new InfraFacility(managementNo, status, JONGNO);
    }

    @Test
    @DisplayName("관리번호가 같은 사업장은 먼저 만난 건만 남기고 중복 수를 센다")
    void dropsDuplicatesByManagementNo() {
        FacilityCollection collection = FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING),
                facility("A-1", BusinessStatus.CLOSED),
                facility("A-2", BusinessStatus.OPERATING)), 2);

        assertThat(collection.readCount()).isEqualTo(2);
        assertThat(collection.duplicatesDropped()).isEqualTo(1);
        assertThat(collection.operatingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("영업/정상이 아닌 사업장은 개수에서 빠지고 필터링 건수로 집계된다")
    void excludesNonOperatingFacilities() {
        FacilityCollection collection = FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING),
                facility("A-2", BusinessStatus.CLOSED),
                facility("A-3", BusinessStatus.SUSPENDED),
                facility("A-4", null)), 1);

        assertThat(collection.readCount()).isEqualTo(4);
        assertThat(collection.operatingCount()).isEqualTo(1);
        assertThat(collection.filteredOutCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 수집도 호출 수는 유지한다")
    void keepsApiCallCountWhenEmpty() {
        FacilityCollection collection = FacilityCollection.of(List.of(), 3);

        assertThat(collection.readCount()).isZero();
        assertThat(collection.operatingCount()).isZero();
        assertThat(collection.apiCalls()).isEqualTo(3);
    }
}
