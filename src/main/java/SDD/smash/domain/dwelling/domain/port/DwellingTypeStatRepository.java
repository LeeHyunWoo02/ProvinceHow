package SDD.smash.domain.dwelling.domain.port;

import SDD.smash.domain.dwelling.domain.model.DwellingTypeStat;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.List;

/** 주택유형별 전월세 시세 저장소 out-port. */
public interface DwellingTypeStatRepository {

    /** 해당 시군구의 유형별 시세. 적재된 유형이 없으면 빈 리스트. */
    List<DwellingTypeStat> findAllBy(SigunguCode code);
}
