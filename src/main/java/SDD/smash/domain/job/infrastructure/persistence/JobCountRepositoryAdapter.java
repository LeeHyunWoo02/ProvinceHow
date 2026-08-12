package SDD.smash.domain.job.infrastructure.persistence;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobCount;
import SDD.smash.domain.job.domain.port.JobCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JobCountRepositoryAdapter implements JobCountRepository {

    private final JobCountJpaRepository jobCountJpaRepository;
    private final JobJpaMapper jobJpaMapper;

    @Override
    public List<RegionJobCount> findAllRegionTotals() {
        return jobCountJpaRepository.findAllRegionTotals().stream()
                .map(jobJpaMapper::toDomain)
                .toList();
    }

    @Override
    public List<RegionJobCount> findAllRegionCountsOf(JobCode jobCode) {
        return jobCountJpaRepository.findAllRegionCountsOf(jobCode.value()).stream()
                .map(jobJpaMapper::toDomain)
                .toList();
    }

    @Override
    public long findTotalOf(SigunguCode sigunguCode) {
        Long sum = jobCountJpaRepository.sumCountBySigunguCode(sigunguCode.value());
        return sum == null ? 0L : sum;
    }

    @Override
    public Optional<Long> findCountOf(SigunguCode sigunguCode, JobCode jobCode) {
        return jobCountJpaRepository
                .findCountBySigunguCodeAndJobCode(sigunguCode.value(), jobCode.value())
                .map(Integer::longValue);
    }
}
