package SDD.smash.domain.address.application.port.in;

import SDD.smash.domain.address.application.dto.RegionCodeView;
import SDD.smash.domain.address.application.dto.SidoView;
import SDD.smash.domain.address.application.dto.SigunguView;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.List;
import java.util.Optional;

/**
 * 행정구역 조회 in-port.
 *
 * <p>다른 컨텍스트(recommendation, job, dwelling, infra, support)가 address 를 호출하는 유일한 통로다.
 * address 의 Aggregate 나 Repository 를 직접 쓰지 않는다.
 */
public interface AddressQueryUseCase {

    List<SidoView> getAllSidos();

    /** 해당 시도가 없으면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다. */
    List<SigunguView> getSigungusBySido(SidoCode sidoCode);

    List<SigunguCode> getAllSigunguCodes();

    List<RegionCodeView> getAllRegionCodes();

    Optional<RegionCodeView> getRegionCode(SigunguCode sigunguCode);

    /** 존재하지 않는 시군구면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다. */
    void checkSigunguExistsOrThrow(SigunguCode sigunguCode);
}
