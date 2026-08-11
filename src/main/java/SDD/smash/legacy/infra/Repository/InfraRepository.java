package SDD.smash.legacy.infra.Repository;

import SDD.smash.legacy.infra.Dto.InfraDetails;
import SDD.smash.legacy.infra.Dto.InfraMajor;
import SDD.smash.legacy.infra.Dto.SigunguMajorAvgDTO;
import SDD.smash.legacy.infra.Entity.Infra;
import SDD.smash.legacy.infra.Entity.Major;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

import java.util.List;

@Repository
public interface InfraRepository extends JpaRepository<Infra,Long> {

    @Query("""
    SELECT new SDD.smash.legacy.infra.Dto.InfraMajor(
        ind.major,
        SUM(i.count),
        AVG(i.score)
    )
    FROM Infra i
    JOIN i.industry ind
    WHERE i.sigungu.sigunguCode = :sigunguCode
      AND ind.major = :major
    GROUP BY ind.major
""")
    Optional<InfraMajor> getInfraMajor(@Param("sigunguCode") String sigunguCode,
                                       @Param("major") Major major);

    @Query("""
    SELECT new SDD.smash.legacy.infra.Dto.InfraDetails(
        ind.major,
        ind.name,
        i.count,
        i.ratio
    )
    FROM Infra i
    JOIN i.industry ind
    WHERE i.sigungu.sigunguCode = :sigunguCode
""")
    List<InfraDetails> getInfraDetails(String sigunguCode);

    @Query("""

            SELECT new SDD.smash.legacy.infra.Dto.SigunguMajorAvgDTO(
            i.sigungu.sigunguCode,
            i.industry.major,
            AVG(i.score)
            )
            FROM Infra i
            WHERE i.industry.major in :majors
            GROUP BY i.sigungu.sigunguCode, i.industry.major
    """)
    List<SigunguMajorAvgDTO> sumScoreBySigunguAndMajor(@Param("majors") Collection<Major> majors);
}
