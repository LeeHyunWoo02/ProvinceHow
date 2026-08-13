package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.domain.model.RegionIndustryStat;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraSnapshot;
import SDD.smash.domain.infra.infrastructure.external.LocalDataApiAdapter;
import SDD.smash.domain.infra.infrastructure.external.LocalDataApiException;
import SDD.smash.domain.infra.infrastructure.external.LocalDataBulkCsvAdapter;
import SDD.smash.domain.infra.infrastructure.master.IndustryMaster;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InfraSnapshotAssemblerTest {

    private static final IndustryCode RESTAURANT = IndustryCode.of("RESTAURANT");
    private static final LocalDataRegionCode JONGNO = LocalDataRegionCode.of("3000000");
    private static final LocalDataRegionCode JUNG = LocalDataRegionCode.of("3010000");

    private InfraMasterCatalog masterCatalog;
    private LocalDataApiAdapter apiAdapter;
    private LocalDataBulkCsvAdapter bulkCsvAdapter;

    @BeforeEach
    void setUp() {
        masterCatalog = mock(InfraMasterCatalog.class);
        apiAdapter = mock(LocalDataApiAdapter.class);
        bulkCsvAdapter = mock(LocalDataBulkCsvAdapter.class);

        IndustryMasterEntry restaurant = new IndustryMasterEntry(RESTAURANT, "일반음식점", Major.FOOD,
                "general_restaurants", "15154916", true, true, null);
        // Map.of 는 null 값을 받지 않는다. "미확인"을 표현해야 하므로 HashMap 을 쓴다.
        Map<String, String> legacyIds = new java.util.LinkedHashMap<>();
        legacyIds.put("07_24_04_P", "RESTAURANT");
        legacyIds.put("11_44_01_P", null);
        given(masterCatalog.industryMaster())
                .willReturn(new IndustryMaster(List.of(restaurant), legacyIds));
        given(masterCatalog.regionCodeMapping()).willReturn(new RegionCodeMapping(List.of(
                new RegionCodeMapping.Entry(JONGNO, SigunguCode.of("11110"), "서울종로구"),
                new RegionCodeMapping.Entry(JUNG, SigunguCode.of("11140"), "서울중구"))));
    }

    private InfraSnapshotAssembler assembler(String source, String legacyPath) {
        return new InfraSnapshotAssembler(masterCatalog, apiAdapter, bulkCsvAdapter,
                source, "PERCENT", legacyPath, "UTF-8", "");
    }

    private static InfraFacility facility(String managementNo, BusinessStatus status, LocalDataRegionCode org) {
        return new InfraFacility(managementNo, status, org);
    }

    // ------------------------------------------------------------------ API 경로

    @Test
    @DisplayName("매핑된 자치단체의 영업중 사업장만 시군구별로 집계한다")
    void aggregatesOperatingFacilitiesPerSigungu() {
        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, JONGNO)).willReturn(FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING, JONGNO),
                facility("A-2", BusinessStatus.CLOSED, JONGNO),
                facility("A-3", BusinessStatus.OPERATING, JONGNO)), 1));
        given(apiAdapter.collect(RESTAURANT, JUNG)).willReturn(FacilityCollection.of(List.of(
                facility("B-1", BusinessStatus.OPERATING, JUNG)), 1));

        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        Map<String, RegionIndustryStat> byRegion = snapshot.rows().stream()
                .collect(java.util.stream.Collectors.toMap(row -> row.sigunguCode().value(), row -> row));
        assertThat(byRegion.get("11110").count()).isEqualTo(2);
        assertThat(byRegion.get("11140").count()).isEqualTo(1);
        assertThat(snapshot.filteredOutCount()).isEqualTo(1);
        assertThat(snapshot.apiCalls()).isEqualTo(2);
        assertThat(snapshot.targets()).isEqualTo(2);
    }

    @Test
    @DisplayName("매핑에 없는 개방자치단체코드의 사업장은 추정하지 않고 제외한다")
    void excludesFacilitiesWithUnmappedOpenOrgCode() {
        LocalDataRegionCode unknown = LocalDataRegionCode.of("9999999");
        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, JONGNO)).willReturn(FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING, JONGNO),
                facility("A-2", BusinessStatus.OPERATING, unknown)), 1));
        given(apiAdapter.collect(RESTAURANT, JUNG)).willReturn(FacilityCollection.empty(1));

        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        assertThat(snapshot.unmappedRegions()).isEqualTo(1);
        assertThat(snapshot.rows()).hasSize(1);
        assertThat(snapshot.rows().get(0).sigunguCode()).isEqualTo(SigunguCode.of("11110"));
        assertThat(snapshot.rows().get(0).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 조합이라도 수집에 실패하면 스냅샷을 만들지 않는다 - 기존 적재분이 보존된다")
    void abortsSnapshotWhenAnyTargetFails() {
        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, JONGNO)).willReturn(FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING, JONGNO)), 1));
        given(apiAdapter.collect(RESTAURANT, JUNG))
                .willThrow(new LocalDataApiException("[localdata] HTTP 429"));

        assertThatThrownBy(() -> assembler("API", "unused.csv").assemble())
                .isInstanceOf(InfraCollectionException.class)
                .hasMessageContaining("수집 실패");
    }

    @Test
    @DisplayName("지역코드 매핑이 비어 있으면 수집 대상이 없어 실패한다")
    void failsWhenRegionMappingIsEmpty() {
        given(apiAdapter.isReady()).willReturn(true);
        given(masterCatalog.regionCodeMapping()).willReturn(RegionCodeMapping.empty());

        assertThatThrownBy(() -> assembler("API", "unused.csv").assemble())
                .isInstanceOf(InfraCollectionException.class)
                .hasMessageContaining("지역코드 매핑");
    }

    @Test
    @DisplayName("활성 업종이 없으면 외부 호출 없이 빈 스냅샷이다")
    void returnsEmptySnapshotWhenNoActiveIndustry() {
        given(masterCatalog.industryMaster()).willReturn(IndustryMaster.empty());

        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        assertThat(snapshot.isEmpty()).isTrue();
        verify(apiAdapter, never()).collect(any(), any());
    }

    @Test
    @DisplayName("인증키가 없으면 준비되지 않은 상태로 보고돼 배치가 건너뛴다")
    void reportsNotReadyWithoutServiceKey() {
        given(apiAdapter.isReady()).willReturn(false);
        given(apiAdapter.readinessDescription()).willReturn("인증키가 비어 있다");

        InfraSnapshotAssembler assembler = assembler("API", "unused.csv");

        assertThat(assembler.isReady()).isFalse();
        assertThat(assembler.readinessDescription()).contains("인증키");
    }

    @Test
    @DisplayName("BULK_CSV 를 고르면 벌크 어댑터가 쓰인다")
    void usesBulkAdapterWhenSelected() {
        given(bulkCsvAdapter.isReady()).willReturn(true);
        given(bulkCsvAdapter.collect(any(), any())).willReturn(FacilityCollection.empty(1));

        InfraSnapshotAssembler assembler = assembler("BULK_CSV", "unused.csv");
        assertThat(assembler.source()).isEqualTo(InfraCollectSource.BULK_CSV);

        assembler.assemble();

        verify(apiAdapter, never()).collect(any(), any());
    }

    @Test
    @DisplayName("알 수 없는 수집 경로 값은 기본값 API 로 돌아간다")
    void fallsBackToApiForUnknownSource() {
        assertThat(assembler("WHATEVER", "unused.csv").source()).isEqualTo(InfraCollectSource.API);
        assertThat(assembler("", "unused.csv").source()).isEqualTo(InfraCollectSource.API);
    }

    // ------------------------------------------------------------------ 레거시 CSV

    @Test
    @DisplayName("레거시 CSV 는 마스터에 등록된 opnSvcId 만 적재하고 나머지는 제외한다")
    void importsOnlyMappedLegacyServiceIds(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("infra.csv");
        Files.writeString(csv, """
                sigungu_code,opnSvcId,num
                11110,07_24_04_P,100
                11140,07_24_04_P,50
                11110,11_44_01_P,26
                11110,99_99_99_P,7
                """, StandardCharsets.UTF_8);

        InfraSnapshotAssembler assembler = assembler("LEGACY_CSV", csv.toString());
        assertThat(assembler.isReady()).isTrue();

        InfraSnapshot snapshot = assembler.assemble();

        assertThat(snapshot.rows()).hasSize(2);
        assertThat(snapshot.unmappedIndustries()).isEqualTo(2);
        assertThat(snapshot.apiCalls()).isZero();
        verify(apiAdapter, never()).collect(any(), any());
    }

    @Test
    @DisplayName("레거시 CSV 가 없으면 실패한다")
    void failsWhenLegacyCsvMissing(@TempDir Path tempDir) {
        assertThatThrownBy(() -> assembler("LEGACY_CSV", tempDir.resolve("nope.csv").toString()).assemble())
                .isInstanceOf(InfraCollectionException.class);
    }

    @Test
    @DisplayName("같은 입력을 두 번 조립하면 결과가 같다 - 같은 기준일 재실행이 멱등하다")
    void producesIdenticalSnapshotForSameInput(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("infra.csv");
        Files.writeString(csv, """
                sigungu_code,opnSvcId,num
                11110,07_24_04_P,100
                11140,07_24_04_P,50
                """, StandardCharsets.UTF_8);

        InfraSnapshotAssembler assembler = assembler("LEGACY_CSV", csv.toString());

        assertThat(assembler.assemble().rows()).isEqualTo(assembler.assemble().rows());
    }
}
