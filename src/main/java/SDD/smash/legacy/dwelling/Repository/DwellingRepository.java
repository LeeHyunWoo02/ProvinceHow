package SDD.smash.legacy.dwelling.Repository;

import SDD.smash.legacy.dwelling.Dto.DwellingInfoDTO;
import SDD.smash.legacy.dwelling.Dto.DwellingJeonseDTO;
import SDD.smash.legacy.dwelling.Dto.DwellingMonthDTO;
import SDD.smash.legacy.dwelling.Dto.DwellingSimpleInfoDTO;
import SDD.smash.legacy.dwelling.Entity.Dwelling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DwellingRepository extends JpaRepository<Dwelling,Long> {

    @Query("""
    SELECT new SDD.smash.legacy.dwelling.Dto.DwellingMonthDTO(
    d.sigungu.sigunguCode,
    d.monthMid
    )
    FROM Dwelling d
    """)
    List<DwellingMonthDTO> getAllDwellingMonth();

    @Query("""
    SELECT new SDD.smash.legacy.dwelling.Dto.DwellingJeonseDTO(
    d.sigungu.sigunguCode,
    d.jeonseMid
    )
    FROM Dwelling d
    """)
    List<DwellingJeonseDTO> getAllDwellingJeonse();

    @Query("""

            SELECT new SDD.smash.legacy.dwelling.Dto.DwellingSimpleInfoDTO(
    d.monthMid,
    d.jeonseMid
    )
    FROM Dwelling d
    WHERE d.sigungu.sigunguCode= :sigunguCode
    """)
    Optional<DwellingSimpleInfoDTO> getDwellingSimpleInfo(String sigunguCode);

    @Query("""

            SELECT new SDD.smash.legacy.dwelling.Dto.DwellingInfoDTO(
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
