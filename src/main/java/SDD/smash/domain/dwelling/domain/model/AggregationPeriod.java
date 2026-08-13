package SDD.smash.domain.dwelling.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 전월세 시세를 집계하는 연속된 월 구간. 양끝을 포함한다.
 *
 * <p>기준월({@code to})에서 과거로 {@code lookbackMonths} 개월을 거슬러 만든다.
 * "몇 개월치를 모아 평균·중앙값을 내는가"는 주거비 도메인의 규칙이므로 값 객체로 못박는다.
 * 배치가 {@code from}/{@code to} 를 각각 들고 다니면 두 값이 어긋난 상태가 만들어질 수 있다.
 */
public record AggregationPeriod(YearMonth from, YearMonth to) {

    /** 집계 구간은 최소 1개월이다. */
    private static final int MIN_MONTHS = 1;

    /**
     * 표본이 지나치게 커지는 것을 막는 상한. 국토부 실거래는 월 단위 공개이고
     * 24개월을 넘기면 시세 변동이 평균에 묻힌다.
     */
    private static final int MAX_MONTHS = 24;

    public AggregationPeriod {
        if (from == null || to == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "집계 구간의 시작월과 기준월은 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "집계 구간의 시작월이 기준월보다 뒤일 수 없습니다.");
        }
    }

    /**
     * 기준월에서 과거로 {@code months} 개월(기준월 포함)을 잡는다.
     *
     * @param months 1 이상 {@value #MAX_MONTHS} 이하
     */
    public static AggregationPeriod endingAt(YearMonth to, int months) {
        if (to == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "기준월은 필수입니다.");
        }
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH,
                    "집계 개월 수는 " + MIN_MONTHS + "~" + MAX_MONTHS + " 범위여야 합니다.");
        }
        return new AggregationPeriod(to.minusMonths(months - 1L), to);
    }

    /** 구간에 포함된 월 수(양끝 포함). */
    public int monthCount() {
        return (int) (java.time.temporal.ChronoUnit.MONTHS.between(from, to) + 1);
    }

    /** 과거 → 현재 순으로 나열한 월 목록. */
    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>(monthCount());
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            months.add(ym);
        }
        return List.copyOf(months);
    }

    public boolean contains(YearMonth yearMonth) {
        return yearMonth != null && !yearMonth.isBefore(from) && !yearMonth.isAfter(to);
    }
}
