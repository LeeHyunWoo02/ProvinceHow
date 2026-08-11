package SDD.smash.dwelling.domain.port;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.domain.model.DwellingMarket;

import java.util.List;
import java.util.Optional;

/** 전월세 시세 저장소 out-port. */
public interface DwellingMarketRepository {

    /** 해당 시군구의 시세가 적재돼 있지 않으면 비어 있다. */
    Optional<DwellingMarket> findBy(SigunguCode code);

    List<DwellingMarket> findAll();
}
