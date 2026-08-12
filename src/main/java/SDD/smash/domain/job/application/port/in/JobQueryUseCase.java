package SDD.smash.domain.job.application.port.in;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.application.dto.JobCategoryView;
import SDD.smash.domain.job.application.dto.JobInfo;
import SDD.smash.domain.job.domain.model.JobCode;

import java.util.List;

/**
 * 일자리 조회 in-port. {@code recommendation} 이 job 을 호출하는 통로다.
 */
public interface JobQueryUseCase {

    /** 해당 시군구의 전체 일자리 정보. 시군구가 없으면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다. */
    JobInfo getJobInfo(SigunguCode sigunguCode);

    /**
     * 해당 시군구·직종의 일자리 정보.
     * 직종 코드가 {@code null} 이면 아무것도 검증하지 않고 {@code null} 을 돌려준다(As-Is 계약).
     * 적재된 행이 없으면 {@code null} 이다.
     */
    JobInfo getJobInfo(SigunguCode sigunguCode, JobCode jobCode);

    List<JobCategoryView> getAllTopCategories();

    /** 해당 대분류가 없으면 {@code JOB_CODE_NOT_FOUND} 를 던진다. */
    List<JobCategoryView> getSubCategoriesOf(JobCode topCode);
}
