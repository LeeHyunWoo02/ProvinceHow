package SDD.smash.job.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobCodeMiddleJpaRepository extends JpaRepository<JobCodeMiddleJpaEntity, String> {

    boolean existsByCode(String code);

    /** As-Is 의 {@code jm.jobCodeTop.code = :topCode} 가 FK 제거 후 그냥 {@code topCode} 가 된다. */
    List<JobCodeMiddleJpaEntity> findAllByTopCode(String topCode);
}
