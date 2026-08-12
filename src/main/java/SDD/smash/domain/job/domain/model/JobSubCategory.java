package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 직종 중분류. {@link JobCategory} Aggregate 안에 속한다.
 * As-Is 의 {@code JobCodeMiddle} 에 해당한다.
 */
public record JobSubCategory(JobCode code, String name, JobCode topCode) {

    public JobSubCategory {
        if (code == null) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "직종 중분류 코드는 필수입니다.");
        }
    }
}
