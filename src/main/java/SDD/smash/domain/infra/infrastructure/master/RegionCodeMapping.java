package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.ArrayList;
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
 * <p>매핑에 없는 개방자치단체코드는 <b>임의 추정하지 않고 제외</b>한다. 주소 문자열 파싱은
 * 코드가 없을 때만 쓰는 보조 수단이며(이 프로젝트는 아직 쓰지 않는다), 코드가 있으면 코드가 우선이다.
 *
 * @param entries 인허가기관 목록. 이 목록이 곧 <b>수집 대상</b>이다
 */
public record RegionCodeMapping(List<Entry> entries) {

    /**
     * @param openOrgCode 개방자치단체코드
     * @param sigunguCode 대응하는 표준 시군구코드
     * @param name        자치단체명(로그·검토용)
     */
    public record Entry(LocalDataRegionCode openOrgCode, SigunguCode sigunguCode, String name) {
    }

    public RegionCodeMapping {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static RegionCodeMapping empty() {
        return new RegionCodeMapping(List.of());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** 수집 대상 인허가기관 코드 목록(파일 순서 유지). */
    public List<LocalDataRegionCode> targets() {
        List<LocalDataRegionCode> targets = new ArrayList<>(entries.size());
        entries.forEach(entry -> targets.add(entry.openOrgCode()));
        return targets;
    }

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
}
