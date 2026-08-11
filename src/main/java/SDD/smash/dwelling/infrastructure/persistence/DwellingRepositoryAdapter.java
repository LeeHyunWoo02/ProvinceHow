package SDD.smash.dwelling.infrastructure.persistence;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.domain.model.DwellingMarket;
import SDD.smash.dwelling.domain.port.DwellingMarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DwellingRepositoryAdapter implements DwellingMarketRepository {

    private final DwellingJpaRepository dwellingJpaRepository;
    private final DwellingJpaMapper dwellingJpaMapper;

    @Override
    public Optional<DwellingMarket> findBy(SigunguCode code) {
        return dwellingJpaRepository.findBySigunguCode(code.value())
                .map(dwellingJpaMapper::toDomain);
    }

    @Override
    public List<DwellingMarket> findAll() {
        return dwellingJpaRepository.findAll().stream()
                .map(dwellingJpaMapper::toDomain)
                .toList();
    }
}
