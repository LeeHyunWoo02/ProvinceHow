package SDD.smash.domain.address.infrastructure.persistence;

import SDD.smash.domain.address.domain.model.RegionCode;
import SDD.smash.domain.address.domain.port.RegionCodeQuery;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RegionCodeQueryAdapter implements RegionCodeQuery {

    private final SigunguJpaRepository sigunguJpaRepository;
    private final SigunguJpaMapper sigunguJpaMapper;

    @Override
    public List<RegionCode> findAll() {
        return sigunguJpaRepository.findAllRegionCodes().stream()
                .map(sigunguJpaMapper::toRegionCode)
                .toList();
    }

    @Override
    public Optional<RegionCode> findBy(SigunguCode code) {
        return sigunguJpaRepository.findRegionCode(code.value())
                .map(sigunguJpaMapper::toRegionCode);
    }
}
