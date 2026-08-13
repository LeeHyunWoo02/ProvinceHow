package SDD.smash.domain.dwelling.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 한 시군구의 집계 구간 전체 수집 결과.
 *
 * <p>평균·중앙값은 <b>표본이 온전할 때만</b> 의미가 있다. 12개월 중 5개월이 장애로 빠진 채
 * 계산한 중앙값을 저장하면, 그 행은 "값이 있는데 틀린 값"이 되어 이후 어떤 검증에도 걸리지 않는다.
 * 그래서 이 타입은 <b>수집 실패한 달을 결과와 같은 급으로</b> 들고 다닌다.
 * 실패를 어떻게 다룰지(중단할지, 부분 집계를 받아들일지)는 이 결과를 받는 쪽이 정한다.
 *
 * @param failedMonths        응답을 신뢰할 수 없어 집계에서 빠진 달
 * @param confirmedEmptyMonths 정상 응답이지만 거래가 0건이던 달. 실패가 아니다
 */
public record RentCollection(SigunguCode sigunguCode,
                             AggregationPeriod period,
                             List<RentRecord> records,
                             int apiCalls,
                             List<YearMonth> failedMonths,
                             List<YearMonth> confirmedEmptyMonths) {

    public RentCollection {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
        if (period == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "집계 구간은 필수입니다.");
        }
        records = (records == null) ? List.of() : List.copyOf(records);
        failedMonths = (failedMonths == null) ? List.of() : List.copyOf(failedMonths);
        confirmedEmptyMonths = (confirmedEmptyMonths == null) ? List.of() : List.copyOf(confirmedEmptyMonths);
    }

    /** 월별 결과를 하나의 수집 결과로 합친다. */
    public static RentCollection from(SigunguCode sigunguCode, AggregationPeriod period,
                                      List<MonthlyRentResult> monthlyResults) {
        List<RentRecord> records = new ArrayList<>();
        List<YearMonth> failed = new ArrayList<>();
        List<YearMonth> empty = new ArrayList<>();
        int calls = 0;

        for (MonthlyRentResult result : monthlyResults) {
            calls += result.apiCalls();
            switch (result.status()) {
                case AVAILABLE -> records.addAll(result.records());
                case CONFIRMED_EMPTY -> empty.add(result.yearMonth());
                case UNDETERMINED -> failed.add(result.yearMonth());
            }
        }
        return new RentCollection(sigunguCode, period, records, calls, failed, empty);
    }

    /** 집계 구간의 모든 달을 확인했는가. 하나라도 실패했으면 표본이 온전하지 않다. */
    public boolean isComplete() {
        return failedMonths.isEmpty();
    }

    public boolean hasFailures() {
        return !failedMonths.isEmpty();
    }

    /** 확인에 성공한 달 수. */
    public int confirmedMonthCount() {
        return period.monthCount() - failedMonths.size();
    }

    public int recordCount() {
        return records.size();
    }
}
