package SDD.smash.domain.dwelling.domain.model;

import SDD.smash.global.domain.model.Money;

/**
 * 한 유형(월세 또는 전세)의 실거래 통계.
 *
 * <p>실거래가 없으면 평균·중앙값이 모두 없을 수 있어 두 필드 모두 null 을 허용한다.
 * "값이 없다"는 것은 As-Is 에서도 정상 상태였다(해당 시군구에 아파트가 없거나 최근 거래가 없는 경우).
 */
public record RentStat(Double average, Money median) {

    public static final RentStat EMPTY = new RentStat(null, null);

    /** 저장소에서 읽은 원시 값으로 복원한다. */
    public static RentStat of(Double average, Integer median) {
        return new RentStat(average, median == null ? null : Money.of(median));
    }

    public boolean hasMedian() {
        return median != null;
    }

    /** 중앙값을 저장용 만원 단위 정수로. 실거래가 없으면 null. */
    public Integer medianManwon() {
        return median == null ? null : median.manwon();
    }
}
