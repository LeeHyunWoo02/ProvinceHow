package SDD.smash.job.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobCodeTopJpaRepository extends JpaRepository<JobCodeTopJpaEntity, String> {

    boolean existsByCode(String code);
}
