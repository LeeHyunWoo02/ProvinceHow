package SDD.smash.domain.infra.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 (업종, 인허가기관) 조합의 수집 결과.
 *
 * <p>중복 제거와 영업상태 필터링을 이 안에서 끝낸다. 어댑터는 "받은 것"만 넘기고
 * "무엇을 셀 것인가"의 판단은 도메인이 한다.
 *
 * @param facilities 중복 제거된 사업장 목록(수집 순서 유지)
 * @param apiCalls   이 결과를 만드는 데 쓴 외부 호출 수. 호출 상한 관리용 지표다
 * @param duplicatesDropped 관리번호가 겹쳐 버린 건수
 */
public record FacilityCollection(List<InfraFacility> facilities, int apiCalls, int duplicatesDropped) {

    public FacilityCollection {
        facilities = facilities == null ? List.of() : List.copyOf(facilities);
    }

    /**
     * 원시 수집 목록에서 관리번호 기준 중복을 제거해 결과를 만든다.
     * 같은 관리번호가 여러 번 나오면 <b>먼저 만난 건</b>을 남긴다.
     */
    public static FacilityCollection of(List<InfraFacility> raw, int apiCalls) {
        if (raw == null || raw.isEmpty()) {
            return new FacilityCollection(List.of(), apiCalls, 0);
        }
        Map<String, InfraFacility> unique = new LinkedHashMap<>();
        int duplicates = 0;
        for (InfraFacility facility : raw) {
            if (facility == null) {
                continue;
            }
            if (unique.putIfAbsent(facility.managementNo(), facility) != null) {
                duplicates++;
            }
        }
        return new FacilityCollection(new ArrayList<>(unique.values()), apiCalls, duplicates);
    }

    public static FacilityCollection empty(int apiCalls) {
        return new FacilityCollection(List.of(), apiCalls, 0);
    }

    /** 영업/정상(01)인 사업장 수. 이 값이 곧 {@code infra.count} 의 원천이다. */
    public int operatingCount() {
        return (int) facilities.stream().filter(InfraFacility::countsAsInfra).count();
    }

    /** 수집한 전체 건수(상태 무관). 필터링 건수를 로그로 남길 때 쓴다. */
    public int readCount() {
        return facilities.size();
    }

    /** 영업상태 필터로 제외된 건수. */
    public int filteredOutCount() {
        return readCount() - operatingCount();
    }
}
