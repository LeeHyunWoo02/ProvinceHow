package SDD.smash.dwelling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DwellingJpaRepository extends JpaRepository<DwellingJpaEntity, Long> {

    /**
     * FK 객체 참조를 없앴으므로 As-Is 의 {@code d.sigungu.sigunguCode} 경로 표현이
     * 그냥 {@code sigunguCode} 가 된다.
     */
    Optional<DwellingJpaEntity> findBySigunguCode(String sigunguCode);
}
