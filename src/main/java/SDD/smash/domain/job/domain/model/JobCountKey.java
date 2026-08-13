package SDD.smash.domain.job.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * {@code JobCount} 한 칸을 가리키는 키. (시군구, 직종 중분류) 쌍이다.
 *
 * <p>{@code JobCount} Aggregate 의 식별자와 같은 조합이다(architecture-conventions §5.1).
 */
public record JobCountKey(SigunguCode sigunguCode, JobCode jobCode) {

    public JobCountKey {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드가 없습니다.");
        }
        if (jobCode == null) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "직종 코드가 없습니다.");
        }
    }
}
