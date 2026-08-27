package SDD.smash.domain.dwelling.domain.model;

/**
 * 주택유형 — 건물의 종류. 실거래 자료의 공급 경로를 가른다.
 *
 * <p>같은 패키지의 {@link DwellingType}(월세/전세, 임대유형)과는 <b>축이 다르다.</b>
 * "아파트의 전세"처럼 두 값은 서로 직교한다.
 */
public enum HousingType {

    /** 아파트 */
    APARTMENT,

    /** 연립다세대 */
    MULTIPLEX_HOUSE,

    /** 단독/다가구 */
    DETACHED_HOUSE
}
