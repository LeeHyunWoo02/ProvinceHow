package SDD.smash.address.domain.port;

import SDD.smash.address.domain.model.Population;
import SDD.smash.common.domain.model.SigunguCode;

import java.util.Optional;

/** 인구 저장소 out-port. */
public interface PopulationRepository {

    /** 해당 시군구의 인구가 적재돼 있지 않으면 비어 있다. */
    Optional<Population> findBy(SigunguCode code);
}
