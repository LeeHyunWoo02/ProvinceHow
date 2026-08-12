package SDD.smash.domain.address.application.port.in;

import SDD.smash.global.domain.model.SigunguCode;

/** 인구 조회 in-port. */
public interface PopulationQueryUseCase {

    /**
     * 해당 시군구의 인구수. 시군구는 존재하지만 인구가 적재되지 않았으면 {@code null} 이다.
     * 존재하지 않는 시군구면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다.
     *
     * <p>As-Is {@code PopulationService.getPopulationBySigunguCode} 의 반환 계약을 그대로 옮겼다.
     */
    Integer getPopulation(SigunguCode sigunguCode);
}
