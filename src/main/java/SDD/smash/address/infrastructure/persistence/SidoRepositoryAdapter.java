package SDD.smash.address.infrastructure.persistence;

import SDD.smash.address.domain.model.Sido;
import SDD.smash.address.domain.port.SidoRepository;
import SDD.smash.common.domain.model.SidoCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SidoRepositoryAdapter implements SidoRepository {

    private final SidoJpaRepository sidoJpaRepository;
    private final SidoJpaMapper sidoJpaMapper;

    @Override
    public List<Sido> findAll() {
        return sidoJpaRepository.findAll().stream()
                .map(sidoJpaMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsBy(SidoCode code) {
        return sidoJpaRepository.existsBySidoCode(code.value());
    }
}
