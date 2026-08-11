package SDD.smash.Address.Repository;

import SDD.smash.Address.Entity.Sido;
import SDD.smash.Apis.Dto.CodeDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SidoRepository extends JpaRepository<Sido,String> {

    @Query("""
    SELECT new SDD.smash.Apis.Dto.CodeDTO(
    s.sidoCode,
    s.name
    )
    FROM Sido s
    """)
    List<CodeDTO> getCodeDTOList();

    boolean existsBySidoCode(String sidoCode);
}
