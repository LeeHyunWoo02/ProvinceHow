package SDD.smash.domain.dwelling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DwellingByTypeJpaRepository extends JpaRepository<DwellingByTypeJpaEntity, Long> {

    List<DwellingByTypeJpaEntity> findAllBySigunguCode(String sigunguCode);
}
