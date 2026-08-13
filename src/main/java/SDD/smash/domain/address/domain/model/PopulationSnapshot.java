package SDD.smash.domain.address.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.time.YearMonth;

/**
 * 특정 <b>통계 기준월</b>의 시군구 인구 한 건.
 *
 * <p>{@link Population} 과 다르다. {@code Population} 은 "현재 적재돼 있는 인구"이고,
 * 이쪽은 "어느 기준월 자료로 관측된 인구"다. 외부 통계는 월 단위로 확정되므로
 * 어느 달 자료인지가 값의 일부다.
 *
 * <p>{@code statisticsMonth} 를 저장하는 컬럼은 아직 없다({@code population} 테이블은
 * {@code sigungu_code}/{@code population_count} 뿐이다). 지금은 로그와 배치 파라미터로만 남는다.
 */
public record PopulationSnapshot(SigunguCode sigunguCode, int count, YearMonth statisticsMonth) {

    public PopulationSnapshot {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
        if (statisticsMonth == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "통계 기준월은 필수입니다.");
        }
        if (count < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "인구수는 0 이상이어야 합니다.");
        }
    }

    public static PopulationSnapshot of(SigunguCode sigunguCode, int count, YearMonth statisticsMonth) {
        return new PopulationSnapshot(sigunguCode, count, statisticsMonth);
    }

    /** 적재 대상 인구로 승격한다. 기준월은 버려진다 — 저장 스키마에 자리가 없다. */
    public Population toPopulation() {
        return Population.of(sigunguCode, count);
    }
}
