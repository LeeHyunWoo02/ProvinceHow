package SDD.smash.Infra.Repository;

<<<<<<< HEAD
import SDD.smash.Infra.Dto.InfraDetails;
import SDD.smash.Infra.Dto.InfraMajor;
import SDD.smash.Infra.Dto.SigunguMajorAvgDTO;
=======
import SDD.smash.Address.Entity.Sigungu;
import SDD.smash.Infra.Entity.Industry;
import SDD.smash.Infra.Dto.InfraDetails;
import SDD.smash.Infra.Dto.InfraMajor;
>>>>>>> origin/Backup/main
import SDD.smash.Infra.Entity.Infra;
import SDD.smash.Infra.Entity.Major;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

<<<<<<< HEAD
import java.util.Collection;
=======
>>>>>>> origin/Backup/main
import java.util.Optional;

import java.util.List;

@Repository
public interface InfraRepository extends JpaRepository<Infra,Long> {
<<<<<<< HEAD
=======
    Optional<Infra> findBySigunguAndIndustry(Sigungu sigungu, Industry industry);
>>>>>>> origin/Backup/main

    @Query("""
    SELECT new SDD.smash.Infra.Dto.InfraMajor(
        ind.major,
<<<<<<< HEAD
        SUM(i.count),
        AVG(i.score)
=======
        SUM(i.count)
>>>>>>> origin/Backup/main
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
    SELECT new SDD.smash.Infra.Dto.InfraDetails(
        ind.major,
        ind.name,
<<<<<<< HEAD
        i.count,
        i.ratio
=======
        i.count
>>>>>>> origin/Backup/main
    )
    FROM Infra i
    JOIN i.industry ind
    WHERE i.sigungu.sigunguCode = :sigunguCode
""")
    List<InfraDetails> getInfraDetails(String sigunguCode);
<<<<<<< HEAD

    @Query("""

            SELECT new SDD.smash.Infra.Dto.SigunguMajorAvgDTO(
            i.sigungu.sigunguCode,
            i.industry.major,
            AVG(i.score)
            )
            FROM Infra i
            WHERE i.industry.major in :majors
            GROUP BY i.sigungu.sigunguCode, i.industry.major
    """)
    List<SigunguMajorAvgDTO> sumScoreBySigunguAndMajor(@Param("majors") Collection<Major> majors);
=======
>>>>>>> origin/Backup/main
}
