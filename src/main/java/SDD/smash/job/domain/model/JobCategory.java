package SDD.smash.job.domain.model;

import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

import java.util.List;

/**
 * 직종 대분류 (Aggregate Root). 중분류 목록을 포함한다.
 *
 * <p>As-Is 의 {@code JobCodeTop} 에 해당한다. 중분류({@link JobSubCategory})는 이 Aggregate 안에 있으므로
 * 루트를 통해서만 꺼낸다.
 *
 * <p>조회 경로에 따라 중분류를 싣지 않고 복원할 수 있다. As-Is 도 대분류 목록과
 * 중분류 목록을 각각 따로 조회했고, 그 호출 패턴을 그대로 유지한다.
 */
public class JobCategory {

    private final JobCode code;
    private final String name;
    private final List<JobSubCategory> subCategories;

    private JobCategory(JobCode code, String name, List<JobSubCategory> subCategories) {
        if (code == null) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "직종 대분류 코드는 필수입니다.");
        }
        this.code = code;
        this.name = name;
        this.subCategories = subCategories == null ? List.of() : List.copyOf(subCategories);
    }

    /** 중분류를 싣지 않고 복원한다. 대분류 목록만 필요한 조회 경로에서 쓴다. */
    public static JobCategory reconstitute(JobCode code, String name) {
        return new JobCategory(code, name, List.of());
    }

    /** 중분류까지 함께 복원한다. */
    public static JobCategory reconstitute(JobCode code, String name, List<JobSubCategory> subCategories) {
        return new JobCategory(code, name, subCategories);
    }

    public JobCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public List<JobSubCategory> subCategories() {
        return subCategories;
    }
}
