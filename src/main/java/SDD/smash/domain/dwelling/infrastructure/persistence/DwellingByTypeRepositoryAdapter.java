package SDD.smash.domain.dwelling.infrastructure.persistence;

import SDD.smash.domain.dwelling.domain.model.DwellingTypeStat;
import SDD.smash.domain.dwelling.domain.port.DwellingTypeStatRepository;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DwellingByTypeRepositoryAdapter implements DwellingTypeStatRepository {

    private final DwellingByTypeJpaRepository dwellingByTypeJpaRepository;
    private final DwellingByTypeJpaMapper dwellingByTypeJpaMapper;

    @Override
    public List<DwellingTypeStat> findAllBy(SigunguCode code) {
        return dwellingByTypeJpaRepository.findAllBySigunguCode(code.value()).stream()
                .map(dwellingByTypeJpaMapper::toDomain)
                .toList();
    }
}
