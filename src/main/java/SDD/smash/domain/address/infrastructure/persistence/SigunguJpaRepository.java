package SDD.smash.domain.address.infrastructure.persistence;

import SDD.smash.domain.address.infrastructure.persistence.projection.RegionCodeRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SigunguJpaRepository extends JpaRepository<SigunguJpaEntity, String> {

    @Query("SELECT s.sigunguCode FROM SigunguJpaEntity s")
    List<String> findAllSigunguCodes();

    boolean existsBySigunguCode(String sigunguCode);

    List<SigunguJpaEntity> findAllBySidoCode(String sidoCode);

    /**
     * As-Is 는 {@code sgg.sido.name} 경로 표현으로 암시적 조인을 걸었다.
     * FK 객체 참조를 없앴으므로 조인을 명시한다. INNER JOIN 이라 결과 집합은 As-Is 와 같다.
     */
    @Query("""
            SELECT new SDD.smash.domain.address.infrastructure.persistence.projection.RegionCodeRow(
                sd.sidoCode,
                sd.name,
                sg.sigunguCode,
                sg.name
            )
            FROM SigunguJpaEntity sg
            JOIN SidoJpaEntity sd ON sd.sidoCode = sg.sidoCode
            """)
    List<RegionCodeRow> findAllRegionCodes();

    @Query("""
            SELECT new SDD.smash.domain.address.infrastructure.persistence.projection.RegionCodeRow(
                sd.sidoCode,
                sd.name,
                sg.sigunguCode,
                sg.name
            )
            FROM SigunguJpaEntity sg
            JOIN SidoJpaEntity sd ON sd.sidoCode = sg.sidoCode
            WHERE sg.sigunguCode = :sigunguCode
            """)
    Optional<RegionCodeRow> findRegionCode(@Param("sigunguCode") String sigunguCode);
}
