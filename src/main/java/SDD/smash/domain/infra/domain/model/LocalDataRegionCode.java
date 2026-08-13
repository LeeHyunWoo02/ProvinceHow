package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 개방자치단체코드. 지방행정 인허가 데이터가 인허가기관(자치단체)을 식별하는 <b>7자리 독자 코드</b>다.
 * 신 API 의 {@code OPN_ATMY_GRP_CD}, 구 LOCALDATA 의 {@code opnSfTeamCode} 에 해당한다.
 *
 * <p><b>표준 시군구코드(5자리)와 산술 관계가 없다.</b> 서울종로구가 {@code 3000000}(=11110),
 * 경기수원시가 {@code 3740000}(=41110) 이라 변환식으로 얻을 수 없고, 응답에 법정동코드 필드도 없다.
 * 그래서 시군구 매핑은 반드시 명시적 매핑표를 통한다
 * ({@code src/main/resources/infra/localdata-region-mapping.yml}).
 *
 * <p>{@code 6110000_ALL} 처럼 시도 전체를 뜻하는 {@code _ALL} 접미 형태도 코드표에 존재하므로
 * 허용한다(벌크 CSV 의 {@code orgCode} 로 그대로 쓸 수 있다).
 */
public record LocalDataRegionCode(String value) {

    private static final String ALL_SUFFIX = "_ALL";
    private static final int DIGITS = 7;

    public LocalDataRegionCode {
        if (value == null || !isValid(value)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND,
                    "유효하지 않은 개방자치단체코드입니다.");
        }
    }

    public static LocalDataRegionCode of(String value) {
        return new LocalDataRegionCode(value == null ? null : value.trim());
    }

    /** 시도 전체를 가리키는 집합 코드인가. */
    public boolean isAggregate() {
        return value.endsWith(ALL_SUFFIX);
    }

    private static boolean isValid(String value) {
        String digits = value.endsWith(ALL_SUFFIX)
                ? value.substring(0, value.length() - ALL_SUFFIX.length())
                : value;
        if (digits.length() != DIGITS) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
