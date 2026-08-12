package SDD.smash.domain.address.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PopulationJpaRepository extends JpaRepository<PopulationJpaEntity, Long> {

    Optional<PopulationJpaEntity> findBySigunguCode(String sigunguCode);
}
