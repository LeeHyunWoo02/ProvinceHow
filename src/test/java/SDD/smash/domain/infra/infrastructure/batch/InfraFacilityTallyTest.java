package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수집 결과 하나를 시군구별 개수로 접는 규칙. 전량 수집 경로와 체크포인트 경로가 공유한다.
 */
class InfraFacilityTallyTest {

    private static final LocalDataRegionCode JONGNO = LocalDataRegionCode.of("3000000");
    private static final LocalDataRegionCode SUWON = LocalDataRegionCode.of("3740000");
    private static final LocalDataRegionCode UNKNOWN = LocalDataRegionCode.of("9999999");

    private final Map<LocalDataRegionCode, SigunguCode> index = Map.of(
            JONGNO, SigunguCode.of("11110"),
            SUWON, SigunguCode.of("41110"));

    @Test
    @DisplayName("영업중인 사업장만 시군구별로 합산한다")
    void countsOnlyOperatingFacilities() {
        FacilityCollection collection = FacilityCollection.of(List.of(
                new InfraFacility("A", BusinessStatus.OPERATING, JONGNO),
                new InfraFacility("B", BusinessStatus.OPERATING, JONGNO),
                new InfraFacility("C", BusinessStatus.CLOSED, JONGNO)), 1);

        InfraFacilityTally.Result result = InfraFacilityTally.tally(collection, index, Map.of());

        assertThat(result.counts()).containsExactly(Map.entry(SigunguCode.of("11110"), 2));
        assertThat(result.countedFacilities()).isEqualTo(2);
    }

    @Test
    @DisplayName("시군구 매핑에 없는 개방자치단체코드는 추정하지 않고 제외한다")
    void excludesUnmappedOrgCode() {
        FacilityCollection collection = FacilityCollection.of(List.of(
                new InfraFacility("A", BusinessStatus.OPERATING, UNKNOWN)), 1);

        InfraFacilityTally.Result result = InfraFacilityTally.tally(collection, index, Map.of());

        assertThat(result.counts()).isEmpty();
        assertThat(result.unmappedFacilityCount()).isEqualTo(1);
        assertThat(result.unmappedRegions()).containsExactly("9999999");
    }

    @Test
    @DisplayName("일반구 시는 주소로 하위 구를 갈라 대상 하나가 여러 시군구를 만든다")
    void splitsGeneralDistrictsByAddress() {
        FacilityCollection collection = FacilityCollection.of(List.of(
                new InfraFacility("A", BusinessStatus.OPERATING, SUWON, "경기도 수원시 장안구 정자동 1", null),
                new InfraFacility("B", BusinessStatus.OPERATING, SUWON, "경기도 수원시 팔달구 인계동 2", null),
                new InfraFacility("C", BusinessStatus.OPERATING, SUWON, "경기도 수원시 팔달구 매교동 3", null)), 1);

        InfraFacilityTally.Result result = InfraFacilityTally.tally(collection, index, Map.of(
                SigunguCode.of("41110"), suwonSplit()));

        assertThat(result.counts()).containsOnly(
                Map.entry(SigunguCode.of("41111"), 1),
                Map.entry(SigunguCode.of("41115"), 2));
        assertThat(result.districtResolved()).isEqualTo(3);
    }

    @Test
    @DisplayName("주소에서 일반구를 찾지 못하면 상위 시로 떨어뜨리지 않고 버린다")
    void dropsFacilityWhenDistrictIsUnresolved() {
        FacilityCollection collection = FacilityCollection.of(List.of(
                new InfraFacility("A", BusinessStatus.OPERATING, SUWON, "경기도 수원시 어딘가 1", null)), 1);

        InfraFacilityTally.Result result = InfraFacilityTally.tally(collection, index, Map.of(
                SigunguCode.of("41110"), suwonSplit()));

        assertThat(result.counts()).isEmpty();
        assertThat(result.districtUnresolved()).isEqualTo(1);
        assertThat(result.unresolvedCities()).containsExactly("수원시");
    }

    private static RegionCodeMapping.DistrictSplit suwonSplit() {
        return new RegionCodeMapping.DistrictSplit(SigunguCode.of("41110"), "수원시", List.of(
                new RegionCodeMapping.District("장안구", SigunguCode.of("41111")),
                new RegionCodeMapping.District("팔달구", SigunguCode.of("41115"))));
    }
}
