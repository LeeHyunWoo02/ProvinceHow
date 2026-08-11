package SDD.smash.Dwelling.Repository;

import SDD.smash.Dwelling.Dto.DwellingInfoDTO;
import SDD.smash.Dwelling.Dto.DwellingJeonseDTO;
import SDD.smash.Dwelling.Dto.DwellingMonthDTO;
import SDD.smash.Dwelling.Dto.DwellingSimpleInfoDTO;
import SDD.smash.Dwelling.Entity.Dwelling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DwellingRepository extends JpaRepository<Dwelling,Long> {

    @Query("""
    SELECT new SDD.smash.Dwelling.Dto.DwellingMonthDTO(
    d.sigungu.sigunguCode,
    d.monthMid
    )
    FROM Dwelling d
    """)
    List<DwellingMonthDTO> getAllDwellingMonth();

    @Query("""
    SELECT new SDD.smash.Dwelling.Dto.DwellingJeonseDTO(
    d.sigungu.sigunguCode,
    d.jeonseMid
    )
    FROM Dwelling d
    """)
    List<DwellingJeonseDTO> getAllDwellingJeonse();

    @Query("""

            SELECT new SDD.smash.Dwelling.Dto.DwellingSimpleInfoDTO(
    d.monthMid,
    d.jeonseMid
    )
    FROM Dwelling d
    WHERE d.sigungu.sigunguCode= :sigunguCode
    """)
    Optional<DwellingSimpleInfoDTO> getDwellingSimpleInfo(String sigunguCode);

    @Query("""

            SELECT new SDD.smash.Dwelling.Dto.DwellingInfoDTO(
    d.monthAvg,
    d.monthMid,
    d.jeonseAvg,
    d.jeonseMid
    )
    FROM Dwelling d
    WHERE d.sigungu.sigunguCode= :sigunguCode
    """)
    Optional<DwellingInfoDTO> getDwellingInfo(String sigunguCode);
}
