package SDD.smash.domain.job.infrastructure.persistence;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.global.domain.model.SigunguCode;
import org.springframework.stereotype.Component;

/** 고용행정통계 도메인 모델 ↔ JPA 엔티티 변환. */
@Component
public class RegionJobStatisticsJpaMapper {

    public RegionJobStatistics toDomain(RegionJobStatisticsJpaEntity entity) {
        RegionJobStatisticsKey key = new RegionJobStatisticsKey(
                SigunguCode.of(entity.getSigunguCode()),
                JobCode.of(entity.getJobTopCode()),
                StatisticsMonth.of(entity.getStatMonth()));

        return RegionJobStatistics.reconstitute(
                key,
                nullZero(entity.getJobOpenings()),
                nullZero(entity.getJobSeekers()),
                nullZero(entity.getPlacements()),
                nullZero(entity.getValidOpenings()),
                nullZero(entity.getValidSeekers()));
    }

    public RegionJobStatisticsJpaEntity toJpaEntity(RegionJobStatistics statistics) {
        return RegionJobStatisticsJpaEntity.builder()
                .sigunguCode(statistics.sigunguCode().value())
                .jobTopCode(statistics.jobCode().value())
                .statMonth(statistics.month().text())
                .jobOpenings(statistics.jobOpenings())
                .jobSeekers(statistics.jobSeekers())
                .placements(statistics.placements())
                .validOpenings(statistics.validOpenings())
                .validSeekers(statistics.validSeekers())
                .build();
    }

    private long nullZero(Long value) {
        return value == null ? 0L : value;
    }
}
