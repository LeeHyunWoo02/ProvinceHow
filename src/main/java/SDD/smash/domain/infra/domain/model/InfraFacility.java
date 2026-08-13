package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 수집된 인허가 사업장 한 건.
 *
 * <p>인프라 개수를 세는 데 필요한 최소 정보만 담는다 —
 * <b>관리번호 / 영업상태 / 인허가기관 / 주소</b>. 업종마다 응답 필드 집합이 달라 이것만이
 * 전 업종 공통으로 신뢰할 수 있는 골격이다(docs/external-api-spec.md §2.3).
 *
 * <p>{@code managementNo}(신 API {@code MNG_NO}, 구 {@code mgtNo})는 자치단체 전역에서
 * 유일해 <b>중복 제거 키</b>로 쓴다. 페이지네이션이 offset 방식이라 수집 중 데이터가 갱신되면
 * 같은 사업장이 두 페이지에 걸쳐 나올 수 있다.
 *
 * <h2>주소를 왜 들고 있는가</h2>
 * LOCALDATA 는 인허가 권한이 있는 <b>시</b> 단위로만 자료를 준다. 수원·성남처럼 일반구를 둔
 * 12개 시는 개방자치단체코드 하나가 시군구코드 여러 개에 대응해, 코드만으로는 일반구를 가릴 수
 * 없다. 응답에 법정동코드 필드도 없어(docs/external-api-spec.md §2.5) 남는 단서가 주소 문자열뿐이다.
 *
 * @param lotAddress  지번주소({@code LOTNO_ADDR} / CSV {@code 지번주소}). 없으면 {@code null}
 * @param roadAddress 도로명주소({@code ROAD_NM_ADDR} / CSV {@code 도로명주소}). 없으면 {@code null}
 */
public record InfraFacility(String managementNo, BusinessStatus status, LocalDataRegionCode openOrgCode,
                            String lotAddress, String roadAddress) {

    public InfraFacility {
        if (managementNo == null || managementNo.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "사업장 관리번호는 필수입니다.");
        }
        managementNo = managementNo.trim();
        lotAddress = blankToNull(lotAddress);
        roadAddress = blankToNull(roadAddress);
    }

    /** 주소를 모르는 사업장. 일반구가 없는 자치단체는 주소 없이도 시군구가 확정된다. */
    public InfraFacility(String managementNo, BusinessStatus status, LocalDataRegionCode openOrgCode) {
        this(managementNo, status, openOrgCode, null, null);
    }

    /** 인프라 개수에 포함되는 사업장인가. 영업/정상(01)만 참이다. */
    public boolean countsAsInfra() {
        return status != null && status.countsAsInfra();
    }

    /**
     * 행정구역을 읽어낼 주소 후보. <b>지번주소가 먼저다.</b>
     *
     * <p>근거 두 가지.
     * <ul>
     *   <li><b>결측률</b> — 실측상 도로명주소는 42.8% 가 비어 있고 지번주소는 대부분 존재한다
     *       (docs/external-api-spec.md §2.5).</li>
     *   <li><b>구조</b> — 지번주소는 {@code 시도 시 구 동 번지} 라 나오는 "구"가 곧 행정구역이다.
     *       도로명주소의 뒷부분은 도로명이라 {@code 수정구길} 처럼 행정구역이 아닌 "구"를
     *       품을 수 있다.</li>
     * </ul>
     *
     * @return 비어 있지 않은 주소만, 우선순위 순서로
     */
    public List<String> addressCandidates() {
        List<String> candidates = new ArrayList<>(2);
        if (lotAddress != null) {
            candidates.add(lotAddress);
        }
        if (roadAddress != null) {
            candidates.add(roadAddress);
        }
        return candidates;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
