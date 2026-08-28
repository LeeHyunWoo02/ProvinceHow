package SDD.smash.domain.job.infrastructure.persistence;

import SDD.smash.IntegrationTestSupport;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.domain.port.RegionJobStatisticsRepository;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월 + 시군구 조회가 실제 MySQL 에서 그 시군구·그 달만 돌려주는지 본다.
 * 지역 상세가 전국을 읽지 않게 된 근거가 이 조회다.
 */
class RegionJobStatisticsRepositoryAdapterTest extends IntegrationTestSupport {

    private static final StatisticsMonth LATEST = StatisticsMonth.of("2026-07");
    private static final StatisticsMonth PREVIOUS = StatisticsMonth.of("2026-06");
    private static final SigunguCode GANGNAM = SigunguCode.of("11680");
    private static final SigunguCode JONGNO = SigunguCode.of("11110");

    @Autowired
    private RegionJobStatisticsRepository regionJobStatisticsRepository;

    @Autowired
    private RegionJobStatisticsJpaRepository regionJobStatisticsJpaRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        regionJobStatisticsJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("월과 시군구가 모두 일치하는 행만 읽는다")
    void readsOnlyRowsMatchingBothMonthAndRegion() {
        // given - 같은 달 다른 시군구, 같은 시군구 다른 달을 함께 넣는다
        save(GANGNAM, "01", LATEST, 300L);
        save(GANGNAM, "02", LATEST, 200L);
        save(JONGNO, "01", LATEST, 9L);
        save(GANGNAM, "01", PREVIOUS, 7L);

        // when
        List<RegionJobStatistics> found =
                regionJobStatisticsRepository.findAllByMonthAndSigunguCode(LATEST, GANGNAM);

        // then
        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(statistics -> {
            assertThat(statistics.sigunguCode()).isEqualTo(GANGNAM);
            assertThat(statistics.month()).isEqualTo(LATEST);
        });
        assertThat(found).extracting(statistics -> statistics.jobCode().value())
                .containsExactlyInAnyOrder("01", "02");
        assertThat(found).extracting(RegionJobStatistics::validOpenings)
                .containsExactlyInAnyOrder(300L, 200L);
    }

    @Test
    @DisplayName("해당 월에 그 시군구 행이 없으면 빈 목록이다")
    void returnsEmptyWhenRegionHasNoRowInThatMonth() {
        // given
        save(JONGNO, "01", LATEST, 9L);

        // when
        List<RegionJobStatistics> found =
                regionJobStatisticsRepository.findAllByMonthAndSigunguCode(LATEST, GANGNAM);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("최신월 조회와 이어 붙이면 그 시군구의 최신월 행만 나온다")
    void combinesWithLatestMonthLookup() {
        // given
        save(GANGNAM, "01", PREVIOUS, 7L);
        save(GANGNAM, "01", LATEST, 300L);

        // when
        StatisticsMonth latest = regionJobStatisticsRepository.findLatestMonth().orElseThrow();
        List<RegionJobStatistics> found =
                regionJobStatisticsRepository.findAllByMonthAndSigunguCode(latest, GANGNAM);

        // then
        assertThat(latest).isEqualTo(LATEST);
        assertThat(found).singleElement()
                .satisfies(statistics -> assertThat(statistics.validOpenings()).isEqualTo(300L));
    }

    private void save(SigunguCode sigunguCode, String jobTopCode, StatisticsMonth month, long validOpenings) {
        regionJobStatisticsJpaRepository.save(RegionJobStatisticsJpaEntity.builder()
                .sigunguCode(sigunguCode.value())
                .jobTopCode(JobCode.of(jobTopCode).value())
                .statMonth(month.text())
                .jobOpenings(10L)
                .jobSeekers(20L)
                .placements(5L)
                .validOpenings(validOpenings)
                .validSeekers(1_000L)
                .build());
    }
}
