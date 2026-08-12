package SDD.smash.domain.job.domain.port;

import SDD.smash.domain.job.domain.model.JobCategory;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobSubCategory;

import java.util.List;

/** 직종 분류 저장소 out-port. */
public interface JobCategoryRepository {

    List<JobCategory> findAllTopCategories();

    List<JobSubCategory> findSubCategoriesOf(JobCode topCode);

    boolean existsTopCategory(JobCode code);

    boolean existsSubCategory(JobCode code);
}
