package SDD.smash.domain.address.infrastructure.persistence;

import SDD.smash.domain.address.domain.model.Population;
import SDD.smash.domain.address.domain.port.PopulationRepository;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PopulationRepositoryAdapter implements PopulationRepository {

    private final PopulationJpaRepository populationJpaRepository;
    private final SigunguJpaMapper sigunguJpaMapper;

    @Override
    public Optional<Population> findBy(SigunguCode code) {
        return populationJpaRepository.findBySigunguCode(code.value())
                // 인구 컬럼이 비어 있으면 "데이터 없음"으로 다룬다.
                // As-Is 도 인구수 프로젝션이 비면 null 을 돌려줬다.
                .filter(entity -> entity.getPopulationCount() != null)
                .map(sigunguJpaMapper::toPopulation);
    }
}
