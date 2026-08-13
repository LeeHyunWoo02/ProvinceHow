package SDD.smash.domain.infra.infrastructure.batch.dto;

import SDD.smash.domain.infra.domain.model.RegionIndustryStat;

import java.util.List;

/**
 * 한 번의 수집으로 만들어진 인프라 스냅샷 전체 + 계량 지표.
 *
 * <p><b>완성된 스냅샷만 적재한다.</b> 수집 도중 하나라도 실패하면 이 객체 자체가 만들어지지 않고
 * 예외가 올라가 Step 이 FAILED 로 끝난다. 그러면 {@code infra} 테이블에는 아무것도 쓰이지 않아
 * 기존 정상 스냅샷이 그대로 남는다.
 *
 * @param rows              적재 대상 통계 행
 * @param targets           (업종 × 자치단체) 수집 시도 조합 수
 * @param apiCalls          외부 호출 수
 * @param readCount         중복 제거 후 읽은 사업장 수
 * @param filteredOutCount  영업상태 필터로 제외된 사업장 수
 * @param duplicateCount    관리번호 중복으로 버린 사업장 수
 * @param unmappedRegions   시군구 매핑에 실패한 자치단체 수
 * @param unmappedIndustries 업종 마스터에 없어 제외한 외부 서비스 식별자 수
 * @param districtResolved   일반구 시에서 주소로 하위 구를 찾아 재분배한 사업장 수
 * @param districtUnresolved 일반구 시인데 주소에서 구를 찾지 못해 <b>제외</b>한 사업장 수.
 *                           상위 시로 떨어뜨리면 일반구와 이중 집계가 되므로 버린다
 */
public record InfraSnapshot(List<RegionIndustryStat> rows,
                            int targets,
                            int apiCalls,
                            int readCount,
                            int filteredOutCount,
                            int duplicateCount,
                            int unmappedRegions,
                            int unmappedIndustries,
                            int districtResolved,
                            int districtUnresolved) {

    public InfraSnapshot {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static InfraSnapshot empty() {
        return new InfraSnapshot(List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
