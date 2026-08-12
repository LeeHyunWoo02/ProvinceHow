package SDD.smash.domain.job.application.dto;

import SDD.smash.domain.job.domain.model.JobCategory;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobSubCategory;

/**
 * 직종 코드-이름 조회 결과. 대분류와 중분류가 같은 모양이라 하나로 쓴다.
 * As-Is 가 둘 다 {@code CodeDTO} 로 내려주던 것과 같다.
 */
public record JobCategoryView(JobCode code, String name) {

    public static JobCategoryView from(JobCategory category) {
        return new JobCategoryView(category.code(), category.name());
    }

    public static JobCategoryView from(JobSubCategory subCategory) {
        return new JobCategoryView(subCategory.code(), subCategory.name());
    }
}
