package SDD.smash.domain.dwelling.domain.model;

/**
 * 전월세 실거래 한 건. 아파트·연립다세대·단독다가구 3종 주택유형에 공통으로 쓰인다.
 *
 * <p>{@code monthlyRent} 가 0 이면 전세, 0 보다 크면 월세로 본다. 이 판정이 집계의 기준이다.
 * 외부 기관의 응답 어휘는 어댑터 안에서 이 타입으로 번역되므로 도메인까지 넘어오지 않는다.
 *
 * <p><b>{@code buildingName} 과 {@code jibun} 은 null 일 수 있다</b> — 단독/다가구 자료에는
 * 두 필드가 없다. 전월세 판정에 쓰이지 않으므로 집계에는 영향이 없다.
 */
public record RentRecord(String buildingName, String jibun, int deposit, int monthlyRent) {

    public boolean isJeonse() {
        return monthlyRent == 0;
    }

    public boolean isMonthly() {
        return monthlyRent > 0;
    }
}
