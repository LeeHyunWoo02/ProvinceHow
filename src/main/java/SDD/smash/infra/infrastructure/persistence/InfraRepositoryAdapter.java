package SDD.smash.infra.infrastructure.persistence;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.infra.domain.model.Major;
import SDD.smash.infra.domain.model.MajorInfraSummary;
import SDD.smash.infra.domain.model.RegionInfra;
import SDD.smash.infra.domain.model.RegionMajorScore;
import SDD.smash.infra.domain.port.InfraMajorSummaryRepository;
import SDD.smash.infra.domain.port.RegionInfraRepository;
import SDD.smash.infra.domain.port.RegionMajorScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code infra} 테이블에 대한 세 조회 포트를 한 어댑터에서 구현한다.
 * 세 포트가 같은 하부 저장소(같은 JPA 리포지토리)를 조회하는 서로 다른 프로젝션이라
 * 어댑터를 나누는 것보다 한 곳에 모으는 편이 낫다고 판단했다.
 */
@Repository
@RequiredArgsConstructor
public class InfraRepositoryAdapter implements RegionInfraRepository, InfraMajorSummaryRepository,
        RegionMajorScoreRepository {

    private final InfraJpaRepository infraJpaRepository;
    private final InfraJpaMapper infraJpaMapper;

    @Override
    public RegionInfra findBy(SigunguCode sigunguCode) {
        return RegionInfra.reconstitute(
                infraJpaRepository.findIndustryCounts(sigunguCode.value()).stream()
                        .map(infraJpaMapper::toDomain)
                        .toList());
    }

    @Override
    public Optional<MajorInfraSummary> findBy(SigunguCode sigunguCode, Major major) {
        return infraJpaRepository.findMajorSummary(sigunguCode.value(), major)
                .map(infraJpaMapper::toDomain);
    }

    @Override
    public List<RegionMajorScore> findAllBy(Set<Major> majors) {
        return infraJpaRepository.findRegionMajorScores(majors).stream()
                .map(infraJpaMapper::toDomain)
                .toList();
    }
}
