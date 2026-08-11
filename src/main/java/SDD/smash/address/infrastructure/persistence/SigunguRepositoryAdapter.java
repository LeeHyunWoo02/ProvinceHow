package SDD.smash.address.infrastructure.persistence;

import SDD.smash.address.domain.model.Sigungu;
import SDD.smash.address.domain.port.SigunguRepository;
import SDD.smash.common.domain.model.SidoCode;
import SDD.smash.common.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SigunguRepositoryAdapter implements SigunguRepository {

    private final SigunguJpaRepository sigunguJpaRepository;
    private final SigunguJpaMapper sigunguJpaMapper;

    @Override
    public List<Sigungu> findAll() {
        return sigunguJpaRepository.findAll().stream()
                .map(sigunguJpaMapper::toDomain)
                .toList();
    }

    @Override
    public List<SigunguCode> findAllCodes() {
        return sigunguJpaRepository.findAllSigunguCodes().stream()
                .map(SigunguCode::of)
                .toList();
    }

    @Override
    public List<Sigungu> findAllBy(SidoCode sidoCode) {
        return sigunguJpaRepository.findAllBySidoCode(sidoCode.value()).stream()
                .map(sigunguJpaMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsBy(SigunguCode code) {
        return sigunguJpaRepository.existsBySigunguCode(code.value());
    }
}
