package SDD.smash.domain.job.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * {@link RegionJobStatistics} 의 식별자. (시군구, 직종 대분류, 기준월) 이다.
 *
 * <p>{@link JobCountKey} 에 기준월이 하나 더 붙은 형태다. EIS 는 월별 통계라
 * 같은 (시군구, 직종) 이라도 월이 다르면 다른 사실(fact)이다.
 */
public record RegionJobStatisticsKey(SigunguCode sigunguCode, JobCode jobCode, StatisticsMonth month) {

    public RegionJobStatisticsKey {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드가 없습니다.");
        }
        if (jobCode == null) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "직종 코드가 없습니다.");
        }
        if (month == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "통계 기준월이 없습니다.");
        }
    }
}
