package SDD.smash.Address.Repository;

import SDD.smash.Address.Dto.SigunguCodeDTO;
import SDD.smash.Address.Entity.Sigungu;
import SDD.smash.Apis.Dto.CodeDTO;
import SDD.smash.Apis.Dto.CodeNameDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SigunguRepository extends JpaRepository<Sigungu,String> {

    @Query("SELECT new SDD.smash.Address.Dto.SigunguCodeDTO(s.sigunguCode) FROM Sigungu s")
    List<SigunguCodeDTO> findAllSigunguCodes();

    boolean existsBySigunguCode(String sigunguCode);


    @Query("""
    SELECT new SDD.smash.Apis.Dto.CodeNameDTO(
    sgg.sido.sidoCode,
    sgg.sido.name,
    sgg.sigunguCode,
    sgg.name
    )
    FROM Sigungu sgg
    """)
    List<CodeNameDTO> findAllCodeNames();

    @Query("""

            SELECT new SDD.smash.Apis.Dto.CodeDTO(
            s.sigunguCode,
            s.name
            )
            FROM Sigungu s
            WHERE s.sido.sidoCode = :sidoCode

    """)
    List<CodeDTO> getCodeDTOListBySidoCode(@Param("sidoCode") String sidoCode);


    @Query("""

            SELECT new SDD.smash.Apis.Dto.CodeNameDTO(
            sgg.sido.sidoCode,
            sgg.sido.name,
            sgg.sigunguCode,
            sgg.name
            )
            FROM Sigungu sgg
            WHERE sgg.sigunguCode = :sigunguCode
    """)
    CodeNameDTO findCodeNameBySigunguCode(@Param("sigunguCode")String sigunguCode);
}
