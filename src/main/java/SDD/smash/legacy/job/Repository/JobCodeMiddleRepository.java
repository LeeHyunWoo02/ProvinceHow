package SDD.smash.legacy.job.Repository;

import SDD.smash.Apis.Dto.CodeDTO;
import SDD.smash.legacy.job.Entity.JobCodeMiddle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobCodeMiddleRepository extends JpaRepository<JobCodeMiddle, String> {

    boolean existsByCode(String code);

    @Query("""
    SELECT new SDD.smash.Apis.Dto.CodeDTO(
    jm.code,
    jm.name
    )
    FROM JobCodeMiddle jm
    WHERE jm.jobCodeTop.code = :topCode
    """)
    List<CodeDTO> getCodeDTOListByTopCode(@Param("topCode") String topCode);
}
