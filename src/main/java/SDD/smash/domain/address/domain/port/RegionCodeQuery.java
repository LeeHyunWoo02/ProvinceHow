package SDD.smash.domain.address.domain.port;

import SDD.smash.domain.address.domain.model.RegionCode;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.List;
import java.util.Optional;

/**
 * 시도-시군구를 합쳐 읽는 조회 전용 out-port (CQRS-lite).
 *
 * <p>Aggregate 두 개를 조인해야 하므로 Aggregate 경로가 아니라 프로젝션으로 채운다.
 */
public interface RegionCodeQuery {

    List<RegionCode> findAll();

    Optional<RegionCode> findBy(SigunguCode code);
}
