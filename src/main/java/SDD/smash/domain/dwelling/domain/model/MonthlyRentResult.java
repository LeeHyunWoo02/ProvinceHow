package SDD.smash.domain.dwelling.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.time.YearMonth;
import java.util.List;

/**
 * 한 시군구의 한 달치 실거래 수집 결과.
 *
 * <p>{@code records} 만 돌려주면 "빈 리스트"가 장애인지 0건인지 구분되지 않는다.
 * 그래서 {@link RentDataStatus} 와, 공급 기관이 스스로 보고한 총건수({@code reportedTotal})를 함께 담는다.
 * {@code reportedTotal} 과 {@code records.size()} 가 다르면 <b>페이지 유실</b>이다.
 *
 * @param apiCalls 이 결과를 만들기 위해 실제로 호출한 횟수(페이지 수). 운영 로그·비용 추적용
 * @param failureReason {@link RentDataStatus#UNDETERMINED} 일 때만 채워지는 사람이 읽는 사유. 비밀값을 담지 않는다
 */
public record MonthlyRentResult(YearMonth yearMonth,
                                List<RentRecord> records,
                                int reportedTotal,
                                int apiCalls,
                                RentDataStatus status,
                                String failureReason) {

    public MonthlyRentResult {
        if (yearMonth == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "수집 대상 연월은 필수입니다.");
        }
        if (status == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "수집 결과 상태는 필수입니다.");
        }
        records = (records == null) ? List.of() : List.copyOf(records);
    }

    /** 정상 응답 + 거래 1건 이상. */
    public static MonthlyRentResult available(YearMonth yearMonth, List<RentRecord> records,
                                              int reportedTotal, int apiCalls) {
        return new MonthlyRentResult(yearMonth, records, reportedTotal, apiCalls,
                RentDataStatus.AVAILABLE, null);
    }

    /** 정상 응답 + 거래 0건(확정). */
    public static MonthlyRentResult confirmedEmpty(YearMonth yearMonth, int apiCalls) {
        return new MonthlyRentResult(yearMonth, List.of(), 0, apiCalls,
                RentDataStatus.CONFIRMED_EMPTY, null);
    }

    /** 응답을 신뢰할 수 없음. 집계에서 제외해야 한다. */
    public static MonthlyRentResult undetermined(YearMonth yearMonth, int apiCalls, String failureReason) {
        return new MonthlyRentResult(yearMonth, List.of(), 0, apiCalls,
                RentDataStatus.UNDETERMINED, failureReason);
    }

    /**
     * 총건수를 알 수 없는 공급자용 축약 팩토리.
     * 건수만으로 판정하므로 "0건"은 확정 0건으로 본다 — 총건수를 주는 공급자라면 쓰지 않는다.
     */
    public static MonthlyRentResult of(YearMonth yearMonth, List<RentRecord> records) {
        List<RentRecord> safe = (records == null) ? List.of() : records;
        return safe.isEmpty()
                ? confirmedEmpty(yearMonth, 1)
                : available(yearMonth, safe, safe.size(), 1);
    }

    /** 공급 기관이 보고한 총건수와 실제로 받아온 건수의 차이. 0이 아니면 유실이다. */
    public int missingCount() {
        return Math.max(0, reportedTotal - records.size());
    }

    public boolean isComplete() {
        return status.isConfirmed() && missingCount() == 0;
    }
}
