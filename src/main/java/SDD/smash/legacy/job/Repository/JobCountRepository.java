package SDD.smash.legacy.job.Repository;

import SDD.smash.legacy.job.Dto.JobCountDTO;
import SDD.smash.legacy.job.Dto.JobInfoDTO;
import SDD.smash.legacy.job.Entity.JobCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobCountRepository extends JpaRepository<JobCount, Long> {

    @Query("""
        SELECT new SDD.smash.legacy.job.Dto.JobCountDTO(
        j.sigungu.sigunguCode,
        SUM(j.count)
        )
        FROM JobCount j
        GROUP BY j.sigungu.sigunguCode
    """)
    List<JobCountDTO> findAllTotalJobCount();


    @Query("""
        SELECT new SDD.smash.legacy.job.Dto.JobCountDTO(
        j.sigungu.sigunguCode,
        j.count
        )
        FROM JobCount j
        WHERE j.jobCodeMiddle.code = :middleCode
    """)
    List<JobCountDTO> findAllJobCode(@Param("middleCode") String middleCode);


    @Query("""
        SELECT new SDD.smash.legacy.job.Dto.JobInfoDTO(
        SUM(j.count)
        )
        FROM JobCount j
        WHERE j.sigungu.sigunguCode = :sigunguCode
    """)
    JobInfoDTO findJobInfo(@Param("sigunguCode") String sigunguCode);

    @Query("""
        SELECT new SDD.smash.legacy.job.Dto.JobInfoDTO(
        j.count
        )
        FROM JobCount j
        WHERE j.sigungu.sigunguCode = :sigunguCode
        AND j.jobCodeMiddle.code = :middleCode
    """)
    JobInfoDTO findJobInfoByCode(@Param("sigunguCode") String sigunguCode, @Param("middleCode") String middleCode);


}
