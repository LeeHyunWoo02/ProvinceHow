package SDD.smash.domain.dwelling.domain.model;

/**
 * 아파트 전월세 실거래 한 건.
 *
 * <p>{@code monthlyRent} 가 0 이면 전세, 0 보다 크면 월세로 본다. 이 판정이 집계의 기준이다.
 * 외부 기관의 응답 어휘는 어댑터 안에서 이 타입으로 번역되므로 도메인까지 넘어오지 않는다.
 */
public record RentRecord(String aptNm, String jibun, int deposit, int monthlyRent) {

    public boolean isJeonse() {
        return monthlyRent == 0;
    }

    public boolean isMonthly() {
        return monthlyRent > 0;
    }
}
