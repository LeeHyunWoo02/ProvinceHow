package SDD.smash.domain.job.infrastructure.persistence;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.domain.port.RegionJobStatisticsRepository;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RegionJobStatisticsRepositoryAdapter implements RegionJobStatisticsRepository {

    private final RegionJobStatisticsJpaRepository regionJobStatisticsJpaRepository;
    private final RegionJobStatisticsJpaMapper regionJobStatisticsJpaMapper;

    @Override
    public Optional<StatisticsMonth> findLatestMonth() {
        return regionJobStatisticsJpaRepository.findLatestStatMonth().map(StatisticsMonth::of);
    }

    @Override
    public List<RegionJobStatistics> findAllByMonth(StatisticsMonth month) {
        return toDomain(regionJobStatisticsJpaRepository.findAllByStatMonth(month.text()));
    }

    @Override
    public List<RegionJobStatistics> findAllByMonthAndJobCode(StatisticsMonth month, JobCode jobCode) {
        return toDomain(regionJobStatisticsJpaRepository
                .findAllByStatMonthAndJobTopCode(month.text(), jobCode.value()));
    }

    @Override
    public List<RegionJobStatistics> findAllByMonthAndSigunguCode(StatisticsMonth month, SigunguCode sigunguCode) {
        return toDomain(regionJobStatisticsJpaRepository
                .findAllByStatMonthAndSigunguCode(month.text(), sigunguCode.value()));
    }

    @Override
    public Optional<RegionJobStatistics> findOne(RegionJobStatisticsKey key) {
        return regionJobStatisticsJpaRepository
                .findBySigunguCodeAndJobTopCodeAndStatMonth(
                        key.sigunguCode().value(), key.jobCode().value(), key.month().text())
                .map(regionJobStatisticsJpaMapper::toDomain);
    }

    @Override
    public List<RegionJobStatistics> findSeriesOf(SigunguCode sigunguCode, JobCode jobCode) {
        return toDomain(regionJobStatisticsJpaRepository
                .findAllBySigunguCodeAndJobTopCodeOrderByStatMonthAsc(sigunguCode.value(), jobCode.value()));
    }

    @Override
    public List<RegionJobStatistics> findAllBySigunguCode(SigunguCode sigunguCode) {
        return toDomain(regionJobStatisticsJpaRepository.findAllBySigunguCode(sigunguCode.value()));
    }

    private List<RegionJobStatistics> toDomain(List<RegionJobStatisticsJpaEntity> entities) {
        return entities.stream().map(regionJobStatisticsJpaMapper::toDomain).toList();
    }
}
