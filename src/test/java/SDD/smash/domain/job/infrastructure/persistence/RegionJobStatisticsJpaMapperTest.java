package SDD.smash.domain.job.infrastructure.persistence;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionJobStatisticsJpaMapperTest {

    private final RegionJobStatisticsJpaMapper mapper = new RegionJobStatisticsJpaMapper();

    @Test
    @DisplayName("도메인 → 엔티티 → 도메인 왕복 변환에서 값이 보존된다")
    void keepsValuesOnRoundTrip() {
        // given
        RegionJobStatistics origin = RegionJobStatistics.of(
                new RegionJobStatisticsKey(SigunguCode.of("11110"), JobCode.of("01"), StatisticsMonth.of("2026-07")),
                179, 210, 54, 200, 886);

        // when
        RegionJobStatisticsJpaEntity entity = mapper.toJpaEntity(origin);
        RegionJobStatistics restored = mapper.toDomain(entity);

        // then
        assertThat(entity.getSigunguCode()).isEqualTo("11110");
        assertThat(entity.getJobTopCode()).isEqualTo("01");
        assertThat(entity.getStatMonth()).isEqualTo("2026-07");

        assertThat(restored.key()).isEqualTo(origin.key());
        assertThat(restored.jobOpenings()).isEqualTo(179L);
        assertThat(restored.jobSeekers()).isEqualTo(210L);
        assertThat(restored.placements()).isEqualTo(54L);
        assertThat(restored.validOpenings()).isEqualTo(200L);
        assertThat(restored.validSeekers()).isEqualTo(886L);
    }

    @Test
    @DisplayName("지표가 null 인 행은 0으로 읽는다")
    void readsNullMeasureAsZero() {
        RegionJobStatisticsJpaEntity entity = RegionJobStatisticsJpaEntity.builder()
                .sigunguCode("11110")
                .jobTopCode("01")
                .statMonth("2026-07")
                .build();

        RegionJobStatistics statistics = mapper.toDomain(entity);

        assertThat(statistics.validOpenings()).isZero();
        assertThat(statistics.jobOpeningRatio()).isEmpty();
    }
}
