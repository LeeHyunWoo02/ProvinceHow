package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 수집 결과 하나(대상 = 자치단체 × 업종)를 <b>시군구별 개수</b>로 접는다.
 *
 * <p>{@code InfraSnapshotAssembler}(전량 수집 경로)와 {@code InfraTargetCollectReader}
 * (체크포인트 수집 경로)가 <b>같은 규칙</b>을 써야 하므로 여기 한 곳에만 둔다.
 * 규칙이 갈리면 두 경로의 집계가 달라져 어느 쪽이 맞는지 알 수 없게 된다.
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>영업/정상이 아닌 사업장은 세지 않는다({@code countsAsInfra}).</li>
 *   <li>시군구 매핑에 없는 개방자치단체코드는 <b>추정하지 않고 제외</b>한다.</li>
 *   <li>일반구를 둔 시는 <b>사업장 주소 문자열</b>로 하위 구를 가른다. 구를 못 찾으면
 *       상위 시로 떨어뜨리지 않고 버린다 — 떨어뜨리면 일반구와 이중 집계가 된다.</li>
 * </ul>
 *
 * <p>순수 함수다. 저장소·시간·랜덤에 의존하지 않는다.
 */
final class InfraFacilityTally {

    private InfraFacilityTally() {
    }

    static Result tally(FacilityCollection collection,
                        Map<LocalDataRegionCode, SigunguCode> index,
                        Map<SigunguCode, RegionCodeMapping.DistrictSplit> splits) {

        Result result = new Result();
        if (collection == null) {
            return result;
        }

        for (InfraFacility facility : collection.facilities()) {
            if (!facility.countsAsInfra()) {
                continue;
            }
            // 사업장이 들고 있는 개방자치단체코드를 우선한다. 요청이 시도 전체(_ALL)일 수 있어
            // 요청 코드로 뭉뚱그리면 시군구가 뭉개진다.
            LocalDataRegionCode orgCode = facility.openOrgCode();
            SigunguCode sigunguCode = orgCode == null ? null : index.get(orgCode);
            if (sigunguCode == null) {
                result.unmappedRegions.add(orgCode == null ? "(없음)" : orgCode.value());
                result.unmappedFacilityCount++;
                continue;
            }

            // 일반구를 둔 시라면 여기서만 주소를 본다. 그 외 지역은 코드로 확정된다.
            RegionCodeMapping.DistrictSplit split = splits.get(sigunguCode);
            if (split != null) {
                SigunguCode district = split.resolveAny(facility.addressCandidates()).orElse(null);
                if (district == null) {
                    result.districtUnresolved++;
                    result.unresolvedCities.add(
                            split.cityName() == null ? sigunguCode.value() : split.cityName());
                    continue;
                }
                result.districtResolved++;
                sigunguCode = district;
            }

            result.counts.merge(sigunguCode, 1, Integer::sum);
            result.countedFacilities++;
        }
        return result;
    }

    /** 대상 하나의 집계 결과와 제외 사유별 계량. */
    static final class Result {

        private final Map<SigunguCode, Integer> counts = new LinkedHashMap<>();
        private final Set<String> unmappedRegions = new LinkedHashSet<>();
        private final Set<String> unresolvedCities = new LinkedHashSet<>();
        private int countedFacilities;
        private int unmappedFacilityCount;
        private int districtResolved;
        private int districtUnresolved;

        Map<SigunguCode, Integer> counts() {
            return counts;
        }

        Set<String> unmappedRegions() {
            return unmappedRegions;
        }

        Set<String> unresolvedCities() {
            return unresolvedCities;
        }

        int countedFacilities() {
            return countedFacilities;
        }

        int unmappedFacilityCount() {
            return unmappedFacilityCount;
        }

        int districtResolved() {
            return districtResolved;
        }

        int districtUnresolved() {
            return districtUnresolved;
        }
    }
}
