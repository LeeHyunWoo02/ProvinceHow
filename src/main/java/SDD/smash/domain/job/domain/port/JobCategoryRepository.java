package SDD.smash.domain.job.domain.port;

import SDD.smash.domain.job.domain.model.JobCategory;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobSubCategory;

import java.util.List;
import java.util.Optional;

/** 직종 분류 저장소 out-port. */
public interface JobCategoryRepository {

    List<JobCategory> findAllTopCategories();

    List<JobSubCategory> findSubCategoriesOf(JobCode topCode);

    /**
     * 중분류가 속한 대분류 코드. 사용자는 중분류로 직종을 고르는데 고용행정통계는 대분류
     * 단위라, 둘을 잇는 역방향 조회가 필요하다. 없는 중분류면 비어 있다.
     */
    Optional<JobCode> findTopCodeOf(JobCode subCode);

    boolean existsTopCategory(JobCode code);

    boolean existsSubCategory(JobCode code);
}
