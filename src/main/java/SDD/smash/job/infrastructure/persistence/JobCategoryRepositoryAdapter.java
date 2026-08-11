package SDD.smash.job.infrastructure.persistence;

import SDD.smash.job.domain.model.JobCategory;
import SDD.smash.job.domain.model.JobCode;
import SDD.smash.job.domain.model.JobSubCategory;
import SDD.smash.job.domain.port.JobCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JobCategoryRepositoryAdapter implements JobCategoryRepository {

    private final JobCodeTopJpaRepository jobCodeTopJpaRepository;
    private final JobCodeMiddleJpaRepository jobCodeMiddleJpaRepository;
    private final JobJpaMapper jobJpaMapper;

    @Override
    public List<JobCategory> findAllTopCategories() {
        return jobCodeTopJpaRepository.findAll().stream()
                .map(jobJpaMapper::toDomain)
                .toList();
    }

    @Override
    public List<JobSubCategory> findSubCategoriesOf(JobCode topCode) {
        return jobCodeMiddleJpaRepository.findAllByTopCode(topCode.value()).stream()
                .map(jobJpaMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsTopCategory(JobCode code) {
        return jobCodeTopJpaRepository.existsByCode(code.value());
    }

    @Override
    public boolean existsSubCategory(JobCode code) {
        return jobCodeMiddleJpaRepository.existsByCode(code.value());
    }
}
