package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 수집된 인허가 사업장 한 건.
 *
 * <p>인프라 개수를 세는 데 필요한 최소 정보만 담는다 —
 * <b>관리번호 / 영업상태 / 인허가기관</b>. 업종마다 응답 필드 집합이 달라 이 셋만이
 * 전 업종 공통으로 신뢰할 수 있는 골격이다(docs/external-api-spec.md §2.3).
 *
 * <p>{@code managementNo}(신 API {@code MNG_NO}, 구 {@code mgtNo})는 자치단체 전역에서
 * 유일해 <b>중복 제거 키</b>로 쓴다. 페이지네이션이 offset 방식이라 수집 중 데이터가 갱신되면
 * 같은 사업장이 두 페이지에 걸쳐 나올 수 있다.
 */
public record InfraFacility(String managementNo, BusinessStatus status, LocalDataRegionCode openOrgCode) {

    public InfraFacility {
        if (managementNo == null || managementNo.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "사업장 관리번호는 필수입니다.");
        }
        managementNo = managementNo.trim();
    }

    /** 인프라 개수에 포함되는 사업장인가. 영업/정상(01)만 참이다. */
    public boolean countsAsInfra() {
        return status != null && status.countsAsInfra();
    }
}
