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
import static org.mockito.Mockito.times;
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

    private static InfraFacility facility(String managementNo, LocalDataRegionCode org,
                                          String lotAddress, String roadAddress) {
        return new InfraFacility(managementNo, BusinessStatus.OPERATING, org, lotAddress, roadAddress);
    }

    /** 수원시(일반구 4개) 하나만 수집 대상으로 두는 매핑. */
    private void givenSplitCityMapping(LocalDataRegionCode openOrgCode, String parentSigunguCode,
                                       String cityName, RegionCodeMapping.District... districts) {
        given(masterCatalog.regionCodeMapping()).willReturn(new RegionCodeMapping(
                List.of(new RegionCodeMapping.Entry(
                        openOrgCode, SigunguCode.of(parentSigunguCode), cityName)),
                List.of(new RegionCodeMapping.DistrictSplit(
                        SigunguCode.of(parentSigunguCode), cityName, List.of(districts)))));
    }

    private static RegionCodeMapping.District district(String name, String sigunguCode) {
        return new RegionCodeMapping.District(name, SigunguCode.of(sigunguCode));
    }

    private static Map<String, RegionIndustryStat> byRegion(InfraSnapshot snapshot) {
        return snapshot.rows().stream()
                .collect(java.util.stream.Collectors.toMap(row -> row.sigunguCode().value(), row -> row));
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
    @DisplayName("1차에서 실패한 대상이 2차 패스에서 성공하면 스냅샷이 정상 생성된다")
    void retriesFailedTargetsOnceAndBuildsSnapshot() {
        // given - 종로는 1차에 성공하고, 중구는 1차 실패 후 2차에 성공한다
        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, JONGNO)).willReturn(FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING, JONGNO),
                facility("A-2", BusinessStatus.OPERATING, JONGNO)), 1));
        given(apiAdapter.collect(RESTAURANT, JUNG))
                .willThrow(new LocalDataApiException("[localdata] 호출 실패 slug=..., page=1"))
                .willReturn(FacilityCollection.of(List.of(
                        facility("B-1", BusinessStatus.OPERATING, JUNG)), 1));

        // when
        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        // then - 두 지역이 모두 스냅샷에 들어간다
        Map<String, RegionIndustryStat> rows = byRegion(snapshot);
        assertThat(rows.get("11110").count()).isEqualTo(2);
        assertThat(rows.get("11140").count()).isEqualTo(1);
        // 1차 실패분은 누계에 반영되지 않았으므로 성공분만 한 번씩 더해진다
        assertThat(snapshot.targets()).isEqualTo(2);
        assertThat(snapshot.apiCalls()).isEqualTo(2);
        assertThat(snapshot.readCount()).isEqualTo(3);
        verify(apiAdapter, times(1)).collect(RESTAURANT, JONGNO);
        verify(apiAdapter, times(2)).collect(RESTAURANT, JUNG);
    }

    @Test
    @DisplayName("2차 패스에서도 실패하면 스냅샷을 만들지 않는다 - 부분 반영이 없다")
    void abortsSnapshotWhenSecondPassAlsoFails() {
        // given - 중구는 두 번 다 실패한다
        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, JONGNO)).willReturn(FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING, JONGNO)), 1));
        given(apiAdapter.collect(RESTAURANT, JUNG))
                .willThrow(new LocalDataApiException("[localdata] HTTP 429"));

        // when / then - 성공한 종로 몫만으로 스냅샷을 만들지 않는다
        assertThatThrownBy(() -> assembler("API", "unused.csv").assemble())
                .isInstanceOf(InfraCollectionException.class)
                .hasMessageContaining("수집 실패")
                .hasMessageContaining("2차 패스");
        verify(apiAdapter, times(2)).collect(RESTAURANT, JUNG);
    }

    @Test
    @DisplayName("1차에서 모두 성공하면 2차 패스를 돌지 않고 누계가 중복 집계되지 않는다")
    void skipsSecondPassWhenEveryTargetSucceedsAtFirstAttempt() {
        // given
        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, JONGNO)).willReturn(FacilityCollection.of(List.of(
                facility("A-1", BusinessStatus.OPERATING, JONGNO),
                facility("A-2", BusinessStatus.CLOSED, JONGNO),
                facility("A-3", BusinessStatus.OPERATING, JONGNO)), 1));
        given(apiAdapter.collect(RESTAURANT, JUNG)).willReturn(FacilityCollection.of(List.of(
                facility("B-1", BusinessStatus.OPERATING, JUNG)), 1));

        // when
        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        // then - 대상마다 정확히 한 번씩만 호출되고 누계는 기존과 같다
        verify(apiAdapter, times(1)).collect(RESTAURANT, JONGNO);
        verify(apiAdapter, times(1)).collect(RESTAURANT, JUNG);
        Map<String, RegionIndustryStat> rows = byRegion(snapshot);
        assertThat(rows.get("11110").count()).isEqualTo(2);
        assertThat(rows.get("11140").count()).isEqualTo(1);
        assertThat(snapshot.targets()).isEqualTo(2);
        assertThat(snapshot.apiCalls()).isEqualTo(2);
        assertThat(snapshot.readCount()).isEqualTo(4);
        assertThat(snapshot.filteredOutCount()).isEqualTo(1);
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

    // ------------------------------------------------------------------ 일반구 재분배

    @Test
    @DisplayName("일반구 시는 사업장 주소의 구 이름으로 하위 구에 재분배한다")
    void redistributesFacilitiesIntoDistrictsByAddress() {
        LocalDataRegionCode suwon = LocalDataRegionCode.of("3740000");
        givenSplitCityMapping(suwon, "41110", "수원시",
                district("장안구", "41111"), district("권선구", "41113"),
                district("팔달구", "41115"), district("영통구", "41117"));

        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, suwon)).willReturn(FacilityCollection.of(List.of(
                facility("S-1", suwon, "경기도 수원시 장안구 정자동 1", null),
                facility("S-2", suwon, "경기도 수원시 장안구 파장동 2", null),
                facility("S-3", suwon, "경기도 수원시 영통구 매탄동 3", null)), 1));

        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        Map<String, RegionIndustryStat> rows = byRegion(snapshot);
        assertThat(rows.get("41111").count()).isEqualTo(2);
        assertThat(rows.get("41117").count()).isEqualTo(1);
        assertThat(snapshot.districtResolved()).isEqualTo(3);
        assertThat(snapshot.districtUnresolved()).isZero();
        // 상위 시 코드로는 한 건도 적재되지 않는다 - 그러면 일반구와 이중 집계가 된다.
        assertThat(rows).doesNotContainKey("41110");
    }

    @Test
    @DisplayName("지번주소가 비면 도로명주소로 구를 찾는다")
    void fallsBackToRoadAddressWhenLotAddressMissing() {
        LocalDataRegionCode suwon = LocalDataRegionCode.of("3740000");
        givenSplitCityMapping(suwon, "41110", "수원시",
                district("장안구", "41111"), district("팔달구", "41115"));

        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, suwon)).willReturn(FacilityCollection.of(List.of(
                facility("S-1", suwon, null, "경기도 수원시 팔달구 매산로 18")), 1));

        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        assertThat(byRegion(snapshot).get("41115").count()).isEqualTo(1);
        assertThat(snapshot.districtResolved()).isEqualTo(1);
    }

    @Test
    @DisplayName("창원시는 긴 이름을 먼저 맞춰 마산합포구와 마산회원구를 가른다")
    void separatesMasanDistrictsByLongestNameFirst() {
        LocalDataRegionCode changwon = LocalDataRegionCode.of("5670000");
        // YAML 이 재정렬돼 짧은 이름이 앞에 오는 최악의 순서를 일부러 만든다.
        givenSplitCityMapping(changwon, "48120", "창원시",
                district("의창구", "48121"), district("성산구", "48123"),
                district("진해구", "48129"),
                district("마산합포구", "48125"), district("마산회원구", "48127"));

        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, changwon)).willReturn(FacilityCollection.of(List.of(
                facility("C-1", changwon, "경상남도 창원시 마산합포구 오동동 1", null),
                facility("C-2", changwon, "경상남도 창원시 마산회원구 석전동 2", null),
                facility("C-3", changwon, "경상남도 창원시 의창구 북면 3", null)), 1));

        Map<String, RegionIndustryStat> rows = byRegion(assembler("API", "unused.csv").assemble());

        assertThat(rows.get("48125").count()).isEqualTo(1);
        assertThat(rows.get("48127").count()).isEqualTo(1);
        assertThat(rows.get("48121").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("주소에서 구를 못 찾으면 상위 시로 떨어뜨리지 않고 매핑 실패로 집계한다")
    void countsUnresolvedDistrictAsMappingFailureWithoutFallingBackToParentCity() {
        LocalDataRegionCode suwon = LocalDataRegionCode.of("3740000");
        givenSplitCityMapping(suwon, "41110", "수원시",
                district("장안구", "41111"), district("권선구", "41113"));

        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, suwon)).willReturn(FacilityCollection.of(List.of(
                facility("S-1", suwon, "경기도 수원시 장안구 정자동 1", null),
                facility("S-2", suwon, "경기도 수원시 매산로 1", null),
                facility("S-3", suwon, null, null)), 1));

        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        Map<String, RegionIndustryStat> rows = byRegion(snapshot);
        assertThat(rows).containsOnlyKeys("41111");
        assertThat(rows.get("41111").count()).isEqualTo(1);
        assertThat(snapshot.districtUnresolved()).isEqualTo(2);
        assertThat(snapshot.districtResolved()).isEqualTo(1);
        assertThat(rows).doesNotContainKey("41110");
    }

    @Test
    @DisplayName("분해 대상이 아닌 시군구는 주소를 보지 않고 기존대로 코드로 집계한다")
    void keepsCodeOnlyMappingForSigunguWithoutDistricts() {
        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, JONGNO)).willReturn(FacilityCollection.of(List.of(
                facility("A-1", JONGNO, null, null),
                facility("A-2", JONGNO, "서울특별시 종로구 세종로 1", null)), 1));
        given(apiAdapter.collect(RESTAURANT, JUNG)).willReturn(FacilityCollection.of(List.of(
                facility("B-1", JUNG, null, null)), 1));

        InfraSnapshot snapshot = assembler("API", "unused.csv").assemble();

        Map<String, RegionIndustryStat> rows = byRegion(snapshot);
        assertThat(rows.get("11110").count()).isEqualTo(2);
        assertThat(rows.get("11140").count()).isEqualTo(1);
        assertThat(snapshot.districtResolved()).isZero();
        assertThat(snapshot.districtUnresolved()).isZero();
    }

    @Test
    @DisplayName("ratio 와 score 는 재분배가 끝난 뒤 일반구 단위로 계산된다")
    void computesRatioAndScoreOnDistrictLevelAfterRedistribution() {
        LocalDataRegionCode suwon = LocalDataRegionCode.of("3740000");
        givenSplitCityMapping(suwon, "41110", "수원시",
                district("장안구", "41111"), district("권선구", "41113"));

        given(apiAdapter.isReady()).willReturn(true);
        given(apiAdapter.collect(RESTAURANT, suwon)).willReturn(FacilityCollection.of(List.of(
                facility("S-1", suwon, "경기도 수원시 장안구 정자동 1", null),
                facility("S-2", suwon, "경기도 수원시 장안구 파장동 2", null),
                facility("S-3", suwon, "경기도 수원시 권선구 세류동 3", null)), 1));

        Map<String, RegionIndustryStat> rows = byRegion(assembler("API", "unused.csv").assemble());

        // 업종이 하나뿐이라 시군구 내 구성비는 각 구에서 100% 다. 상위 시로 뭉쳤다면 행이 하나였을 것이다.
        assertThat(rows).containsOnlyKeys("41111", "41113");
        assertThat(rows.get("41111").ratio().value()).isEqualByComparingTo(new java.math.BigDecimal("100.00"));
        assertThat(rows.get("41113").ratio().value()).isEqualByComparingTo(new java.math.BigDecimal("100.00"));
        // 백분위 모집단이 일반구 2곳이다. 많은 쪽 100점, 적은 쪽 0점.
        assertThat(rows.get("41111").score().value()).isEqualByComparingTo(new java.math.BigDecimal("100.00"));
        assertThat(rows.get("41113").score().value()).isEqualByComparingTo(new java.math.BigDecimal("0.00"));
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
