package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 개방자치단체코드(7자리) → 표준 시군구코드(5자리) 매핑.
 *
 * <p><b>산술 변환이 불가능하다.</b> 서울종로구 {@code 3000000} = {@code 11110},
 * 경기수원시 {@code 3740000} = {@code 41110} 처럼 두 체계 사이에 규칙이 없고
 * 응답에 법정동코드 필드도 없다. 그래서 매핑을 파일로 명시한다.
 *
 * <p>매핑에 없는 개방자치단체코드는 <b>임의 추정하지 않고 제외</b>한다.
 *
 * <h2>일반구 분해({@code districtSplits})</h2>
 * LOCALDATA 는 인허가 권한이 있는 <b>시</b> 단위로만 자료를 준다. 수원·성남·안양·부천·안산·
 * 고양·용인·화성·청주·천안·포항·창원·전주 13개 시는 개방자치단체코드 하나가 일반구 시군구코드
 * 여러 개에 대응한다. 이 경우에만 <b>사업장 주소 문자열의 구 이름</b>으로 하위 구를 가른다
 * (정책 결정 2026-08-13). 상위 시 값을 복제하지 않는다.
 *
 * @param entries        인허가기관 목록. 이 목록이 곧 <b>수집 대상</b>이다
 * @param districtSplits 상위 시 시군구코드 → 하위 일반구 분해 규칙
 */
public record RegionCodeMapping(List<Entry> entries, List<DistrictSplit> districtSplits) {

    /**
     * @param openOrgCode 개방자치단체코드
     * @param sigunguCode 대응하는 표준 시군구코드
     * @param name        자치단체명(로그·검토용)
     */
    public record Entry(LocalDataRegionCode openOrgCode, SigunguCode sigunguCode, String name) {
    }

    /**
     * 일반구 하나.
     *
     * @param name        주소 문자열에서 찾을 구 이름(예: {@code 마산합포구})
     * @param sigunguCode 그 구의 표준 시군구코드
     */
    public record District(String name, SigunguCode sigunguCode) {
    }

    /**
     * 상위 시 하나의 분해 규칙.
     *
     * <p>{@code districts} 는 <b>이름 길이 내림차순으로 정렬해서 보관</b>한다. YAML 의 나열 순서에
     * 기대지 않기 위해서다 — 파일을 누가 재정렬해도 {@code 마산합포구}/{@code 마산회원구},
     * {@code 일산동구}/{@code 일산서구} 같은 짝에서 짧은 이름이 먼저 걸리는 오매칭이 생기지 않는다.
     */
    public record DistrictSplit(SigunguCode parentSigunguCode, String cityName, List<District> districts) {

        /** 이름이 길수록 먼저. 같은 길이면 파일 순서를 유지한다(안정 정렬). */
        private static final Comparator<District> LONGEST_NAME_FIRST =
                Comparator.comparingInt((District district) -> district.name().length()).reversed();

        public DistrictSplit {
            List<District> sorted = new ArrayList<>(districts == null ? List.of() : districts);
            sorted.sort(LONGEST_NAME_FIRST);
            districts = List.copyOf(sorted);
        }

        /**
         * 주소 문자열 하나에서 구를 찾는다.
         *
         * <h3>여러 구 이름이 나오면 — 가장 앞에 나온 것</h3>
         * 주소는 {@code 시도 시 구 동 번지} 순의 큰 단위 우선 표기라 <b>행정구역으로서의 구는
         * 항상 맨 앞에 나온다.</b> 뒤쪽에 다시 나오는 "구"는 도로명({@code 수정구길})이나
         * 건물명({@code 수정구빌딩})처럼 행정구역이 아닌 문자열이다. 그래서
         * {@code 경기도 성남시 분당구 정자동 178 수정구빌딩} 은 분당구다.
         *
         * <p>같은 위치에서 두 이름이 걸리면(한쪽이 다른 쪽의 접두어인 경우) <b>긴 이름</b>이 이긴다.
         * {@code districts} 가 길이 내림차순이고 비교를 {@code <} 로 하므로 자연히 그렇게 된다.
         *
         * @return 구를 찾지 못하면 {@link Optional#empty()} — <b>상위 시로 떨어뜨리지 않는다</b>
         */
        public Optional<SigunguCode> resolve(String address) {
            if (address == null || address.isBlank()) {
                return Optional.empty();
            }
            District matched = null;
            int bestIndex = Integer.MAX_VALUE;
            for (District district : districts) {
                int index = address.indexOf(district.name());
                if (index >= 0 && index < bestIndex) {
                    bestIndex = index;
                    matched = district;
                }
            }
            return Optional.ofNullable(matched).map(District::sigunguCode);
        }

        /**
         * 주소 후보를 우선순위대로 훑어 처음 걸리는 구를 쓴다.
         *
         * <p>후보 순서는 {@code InfraFacility.addressCandidates()} 가 정한다(지번주소 우선).
         * 앞 후보에서 구를 찾으면 뒤 후보는 보지 않는다 — 둘이 다르면 신뢰도가 높은 쪽을 택해야 하고,
         * 그 판단은 이미 순서로 표현돼 있다.
         */
        public Optional<SigunguCode> resolveAny(List<String> addresses) {
            if (addresses == null) {
                return Optional.empty();
            }
            for (String address : addresses) {
                Optional<SigunguCode> resolved = resolve(address);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
            return Optional.empty();
        }
    }

    public RegionCodeMapping {
        entries = entries == null ? List.of() : List.copyOf(entries);
        districtSplits = districtSplits == null ? List.of() : List.copyOf(districtSplits);
    }

    /** {@code districtSplits} 가 없는 옛 형식 호환. */
    public RegionCodeMapping(List<Entry> entries) {
        this(entries, List.of());
    }

    public static RegionCodeMapping empty() {
        return new RegionCodeMapping(List.of(), List.of());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** 수집 대상 인허가기관 코드 목록(파일 순서 유지). 테스트 전용 — 프로덕션은 {@link #asMap()} 을 쓴다. */
    public List<LocalDataRegionCode> targets() {
        List<LocalDataRegionCode> targets = new ArrayList<>(entries.size());
        entries.forEach(entry -> targets.add(entry.openOrgCode()));
        return targets;
    }

    /** 단건 조회. 테스트 전용 — 프로덕션은 {@link #asMap()} 인덱스를 쓴다. */
    public Optional<SigunguCode> toSigungu(LocalDataRegionCode openOrgCode) {
        if (openOrgCode == null) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(entry -> entry.openOrgCode().equals(openOrgCode))
                .map(Entry::sigunguCode)
                .findFirst();
    }

    /** 조회를 여러 번 할 때 쓰는 인덱스. */
    public Map<LocalDataRegionCode, SigunguCode> asMap() {
        Map<LocalDataRegionCode, SigunguCode> map = new LinkedHashMap<>();
        entries.forEach(entry -> map.putIfAbsent(entry.openOrgCode(), entry.sigunguCode()));
        return map;
    }

    /** 이 시군구코드가 일반구로 분해되는 상위 시인가. 테스트 전용 — 프로덕션은 {@link #splitIndex()} 를 쓴다. */
    public Optional<DistrictSplit> splitOf(SigunguCode parentSigunguCode) {
        if (parentSigunguCode == null) {
            return Optional.empty();
        }
        return districtSplits.stream()
                .filter(split -> parentSigunguCode.equals(split.parentSigunguCode()))
                .findFirst();
    }

    /** 사업장마다 조회하므로 인덱스를 미리 만든다. */
    public Map<SigunguCode, DistrictSplit> splitIndex() {
        Map<SigunguCode, DistrictSplit> index = new LinkedHashMap<>();
        districtSplits.forEach(split -> index.putIfAbsent(split.parentSigunguCode(), split));
        return index;
    }
}
