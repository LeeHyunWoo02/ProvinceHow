package SDD.smash.domain.job.infrastructure.persistence;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobCategory;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobSubCategory;
import SDD.smash.domain.job.domain.model.RegionJobCount;
import SDD.smash.domain.job.infrastructure.persistence.projection.RegionJobCountRow;
import org.springframework.stereotype.Component;

/** job 도메인 모델 ↔ JPA 엔티티/프로젝션 변환. */
@Component
public class JobJpaMapper {

    public JobCategory toDomain(JobCodeTopJpaEntity entity) {
        return JobCategory.reconstitute(JobCode.of(entity.getCode()), entity.getName());
    }

    public JobSubCategory toDomain(JobCodeMiddleJpaEntity entity) {
        return new JobSubCategory(
                JobCode.of(entity.getCode()),
                entity.getName(),
                JobCode.of(entity.getTopCode()));
    }

    /** 집계 결과가 비면 0 으로 채운다. As-Is {@code JobCountDTO} 의 null 보정을 그대로 옮긴 것이다. */
    public RegionJobCount toDomain(RegionJobCountRow row) {
        long count = row.count() == null ? 0L : row.count();
        return new RegionJobCount(SigunguCode.of(row.sigunguCode()), count);
    }
}
