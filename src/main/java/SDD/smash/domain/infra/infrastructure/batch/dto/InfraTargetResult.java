package SDD.smash.domain.infra.infrastructure.batch.dto;

import SDD.smash.global.domain.model.SigunguCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 대상 하나를 <b>끝까지</b> 수집한 결과. {@code infraCollectStep} 의 Reader 산출물이다.
 *
 * <p>{@code counts} 는 일반구 재분배까지 끝난 시군구 코드만 담는다. 비어 있어도 정상이다 —
 * 그 대상에 영업중 시설이 없다는 뜻이고, 그래도 <b>수집 완료로 기록</b>해야 회차 완성 여부를 셀 수 있다.
 *
 * @param counts               시군구 → 개수
 * @param facilityCount        {@code counts} 합계(검산용)
 * @param apiCalls             이 대상이 쓴 외부 호출 수
 * @param readCount            중복 제거 후 읽은 사업장 수
 * @param filteredOut          영업상태 필터로 제외된 사업장 수
 * @param duplicates           관리번호 중복으로 버린 사업장 수
 * @param unmappedFacilities   시군구 매핑이 없어 제외한 사업장 수
 * @param districtResolved     주소로 일반구를 찾아 재분배한 사업장 수
 * @param districtUnresolved   일반구를 찾지 못해 제외한 사업장 수
 */
public record InfraTargetResult(InfraCollectTarget target,
                                Map<SigunguCode, Integer> counts,
                                int facilityCount,
                                int apiCalls,
                                int readCount,
                                int filteredOut,
                                int duplicates,
                                int unmappedFacilities,
                                int districtResolved,
                                int districtUnresolved) {

    public InfraTargetResult {
        counts = counts == null ? Map.of() : new LinkedHashMap<>(counts);
    }
}
