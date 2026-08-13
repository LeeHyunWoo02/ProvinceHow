package SDD.smash.domain.infra.domain.model;

/**
 * 사업장의 영업상태. 지방행정 인허가 데이터의 <b>공식 영업상태코드</b>를 도메인 언어로 옮긴 것이다.
 *
 * <p>코드 값은 공식 참고문서 {@code 개방자치단체코드_영업상태코드.xlsx} 의 시트
 * {@code 2. 영업상태코드} 정본이다(docs/external-api-spec.md §2.4).
 * 구 LOCALDATA 의 {@code trdStateGbn} 과 값 체계가 같고, 신 API 에서는 {@code SALS_STTS_CD} 다.
 *
 * <p><b>상세영업상태코드({@code DTL_SALS_STTS_CD})는 쓰지 않는다.</b> 업종마다 값 체계가 달라
 * 전 업종 통합 코드표가 배포되지 않는다.
 *
 * <p>인프라 집계에 포함되는 것은 {@link #OPERATING} 하나뿐이다. 휴업·폐업·취소·삭제는
 * "지금 그 지역에서 이용할 수 있는 시설"이 아니므로 제외한다.
 */
public enum BusinessStatus {

    /** 01 — 영업/정상. 집계 대상이다. */
    OPERATING("01"),
    /** 02 — 휴업 */
    SUSPENDED("02"),
    /** 03 — 폐업 */
    CLOSED("03"),
    /** 04 — 취소/말소/만료/정지/중지 */
    REVOKED("04"),
    /** 05 — 제외/삭제/전출 */
    REMOVED("05"),
    /** 06 — 기타 */
    ETC("06");

    private final String code;

    BusinessStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 이 상태의 사업장을 인프라 개수에 포함하는가. */
    public boolean countsAsInfra() {
        return this == OPERATING;
    }

    /**
     * 공식 코드 문자열을 상태로 바꾼다. 앞뒤 공백과 한 자리 표기({@code "1"})를 허용한다.
     *
     * @return 코드표에 없는 값이면 {@code null}. 외부 데이터라 미지의 값이 올 수 있고,
     *         그 판단(제외/집계)은 호출부의 책임이다
     */
    public static BusinessStatus fromCode(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() == 1) {
            trimmed = "0" + trimmed;
        }
        for (BusinessStatus status : values()) {
            if (status.code.equals(trimmed)) {
                return status;
            }
        }
        return null;
    }
}
