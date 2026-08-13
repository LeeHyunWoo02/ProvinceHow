package SDD.smash.domain.dwelling.domain.model;

/**
 * 어떤 시군구·어떤 월의 실거래 자료를 "받아왔다"고 말할 수 있는지에 대한 판정.
 *
 * <p><b>"응답이 비어 있다"와 "실제로 거래가 0건이다"는 다른 사실이다.</b>
 * 전자는 장애·인증오류·미확정이고 후자는 정상 데이터다. 둘을 섞으면
 * 장애가 난 달을 "거래 없는 달"로 집계에 넣게 되어 평균·중앙값이 조용히 왜곡된다.
 *
 * <p>이 구분의 근거는 공급 기관 응답의 <em>성공 여부</em>와 <em>보고된 총건수</em> 두 가지다.
 * 어느 필드가 그 역할을 하는지는 어댑터가 알고, 도메인은 판정 결과만 안다.
 */
public enum RentDataStatus {

    /** 정상 응답이고 거래가 1건 이상이다. */
    AVAILABLE,

    /** 정상 응답이지만 거래가 0건이다. 집계에 "0건"으로 반영해도 되는 확정 사실이다. */
    CONFIRMED_EMPTY,

    /** 정상 응답을 받지 못했다. 0건인지 아닌지 알 수 없으므로 집계에 넣으면 안 된다. */
    UNDETERMINED;

    /** 공급 기관이 사실을 확인해 준 상태인가. */
    public boolean isConfirmed() {
        return this != UNDETERMINED;
    }

    public boolean hasData() {
        return this == AVAILABLE;
    }
}
