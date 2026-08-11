package SDD.smash.legacy.job.Repository;

import SDD.smash.Apis.Dto.CodeDTO;
import SDD.smash.legacy.job.Entity.JobCodeTop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobCodeTopRepository extends JpaRepository<JobCodeTop, String> {

    @Query("""
    SELECT new SDD.smash.Apis.Dto.CodeDTO(
    jt.code,
    jt.name
    )
    FROM JobCodeTop jt
    """)
    List<CodeDTO> getCodeDTOList();

    boolean existsByCode(String code);

}
