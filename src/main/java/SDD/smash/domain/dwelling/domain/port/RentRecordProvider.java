package SDD.smash.domain.dwelling.domain.port;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.MonthlyRentResult;
import SDD.smash.domain.dwelling.domain.model.RentCollection;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.global.domain.model.SigunguCode;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 실거래 자료 공급 out-port.
 *
 * <p>어느 기관의 어떤 프로토콜인지는 어댑터가 안다. 도메인은 "월 단위로 실거래를 받아온다"만 안다.
 *
 * <p>조회 메서드가 셋인 이유는 <b>실패를 어떻게 알릴지가 호출자마다 다르기 때문</b>이다.
 * <ul>
 *   <li>{@link #fetch} — 엄격. 실패하면 예외를 던진다. 재시도 정책이 걸린 배치 Step 이 쓴다</li>
 *   <li>{@link #fetchMonth} — 관대. 실패를 {@link MonthlyRentResult} 상태로 돌려준다.
 *       "이 달에 자료가 있는가"를 확인하는 탐침용이다</li>
 *   <li>{@link #collect} — 구간 단위. 실패한 달을 모아서 돌려주므로 <b>부분 실패를 성공으로
 *       덮지 않는다</b></li>
 * </ul>
 */
public interface RentRecordProvider {

    /**
     * 해당 시군구의 해당 월 실거래 목록. 거래가 없으면 빈 리스트.
     *
     * @throws RuntimeException 공급 기관 응답을 신뢰할 수 없을 때. 빈 리스트로 삼키지 않는다
     */
    List<RentRecord> fetch(SigunguCode code, YearMonth yearMonth);

    /**
     * 실패를 예외 대신 상태로 돌려주는 조회.
     *
     * <p>기본 구현은 {@link #fetch} 를 감싸므로 총건수를 알 수 없어 "0건"을 확정 0건으로 본다.
     * 총건수를 주는 공급자는 이 메서드를 재정의해 {@code CONFIRMED_EMPTY} 와 {@code UNDETERMINED} 를
     * 실제 응답으로 구분해야 한다.
     */
    default MonthlyRentResult fetchMonth(SigunguCode code, YearMonth yearMonth) {
        try {
            return MonthlyRentResult.of(yearMonth, fetch(code, yearMonth));
        } catch (RuntimeException e) {
            return MonthlyRentResult.undetermined(yearMonth, 1,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 집계 구간 전체를 모은다. 한 달이 실패해도 나머지 달을 계속 확인하고,
     * 실패한 달 목록을 결과에 담아 돌려준다.
     */
    default RentCollection collect(SigunguCode code, AggregationPeriod period) {
        List<MonthlyRentResult> results = new ArrayList<>(period.monthCount());
        for (YearMonth yearMonth : period.months()) {
            results.add(fetchMonth(code, yearMonth));
        }
        return RentCollection.from(code, period, results);
    }
}
