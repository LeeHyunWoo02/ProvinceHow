package SDD.smash.domain.dwelling.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.dwelling.domain.model.RentRecord;

import java.time.YearMonth;
import java.util.List;

/**
 * 외부 실거래 자료 공급 out-port.
 *
 * <p>어느 기관의 어떤 프로토콜인지는 어댑터가 안다. 도메인은 "월 단위로 실거래를 받아온다"만 안다.
 */
public interface RentRecordProvider {

    /** 해당 시군구의 해당 월 실거래 목록. 없으면 빈 리스트. */
    List<RentRecord> fetch(SigunguCode code, YearMonth yearMonth);
}
