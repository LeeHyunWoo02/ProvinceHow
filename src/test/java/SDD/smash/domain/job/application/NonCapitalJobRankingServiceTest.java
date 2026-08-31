package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.NonCapitalRankView;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.NonCapitalRatioSnapshot;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.domain.port.NonCapitalRatioCache;
import SDD.smash.domain.job.domain.port.RegionJobStatisticsRepository;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NonCapitalJobRankingServiceTest {

    private static final StatisticsMonth MONTH = StatisticsMonth.of("2026-07");
    private static final SigunguCode MOKPO = SigunguCode.of("46110");
    private static final SigunguCode GANGNAM = SigunguCode.of("11680");

    @Mock RegionJobStatisticsRepository regionJobStatisticsRepository;
    @Mock NonCapitalRatioCache nonCapitalRatioCache;

    @InjectMocks NonCapitalJobRankingService service;

    @Test
    @DisplayName("비수도권 지역은 합계 구인배수의 백분위를 받는다")
    void ranksNonCapitalRegion() {
        // given
        givenMonthRows(List.of(
                statistics("46110", "01", 100L, 1_000L),   // 0.1
                statistics("46130", "01", 300L, 1_000L),   // 0.3
                statistics("46150", "01", 500L, 1_000L))); // 0.5

        // when
        NonCapitalRankView rank = service.getRegionRank(MOKPO).orElseThrow();

        // then - 가장 낮은 배수라 3위, 모집단 3
        assertThat(rank.rank()).isEqualTo(3);
        assertThat(rank.total()).isEqualTo(3);
        assertThat(rank.topPercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("수도권 지역을 조회하면 백분위가 비어 있다")
    void leavesRankEmptyForCapitalArea() {
        givenMonthRows(List.of(
                statistics("11680", "01", 500L, 1_000L),
                statistics("46110", "01", 100L, 1_000L)));

        assertThat(service.getRegionRank(GANGNAM)).isEmpty();
        assertThat(service.getRegionRankByJob(GANGNAM)).isEmpty();
    }

    @Test
    @DisplayName("구인배수가 없으면(유효구직자수 0) 백분위도 비어 있다")
    void leavesRankEmptyWhenRatioIsNotComputable() {
        givenMonthRows(List.of(
                statistics("46110", "01", 100L, 0L),
                statistics("46130", "01", 300L, 1_000L)));

        assertThat(service.getRegionRank(MOKPO)).isEmpty();
    }

    @Test
    @DisplayName("직종별 백분위는 직종마다 따로 매겨진다")
    void ranksEachJobCategorySeparately() {
        // given - 목포는 05 에서 1위, 06 에서 2위
        givenMonthRows(List.of(
                statistics("46110", "05", 500L, 1_000L),
                statistics("46130", "05", 100L, 1_000L),
                statistics("46110", "06", 100L, 1_000L),
                statistics("46130", "06", 500L, 1_000L)));

        Map<String, NonCapitalRankView> ranks = service.getRegionRankByJob(MOKPO);

        assertThat(ranks.get("05").rank()).isEqualTo(1);
        assertThat(ranks.get("06").rank()).isEqualTo(2);
    }

    @Test
    @DisplayName("캐시에 최신월 분포가 있으면 통계를 다시 읽지 않는다")
    void usesCachedSnapshotWithoutQueryingStatistics() {
        // given
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.of(MONTH));
        given(nonCapitalRatioCache.find(MONTH)).willReturn(Optional.of(NonCapitalRatioSnapshot.of(
                MONTH, Map.of(MOKPO, 0.5), Map.of())));

        // when
        NonCapitalRankView rank = service.getRegionRank(MOKPO).orElseThrow();

        // then
        assertThat(rank.total()).isEqualTo(1);
        then(regionJobStatisticsRepository).should(never()).findAllByMonth(any());
        then(nonCapitalRatioCache).should(never()).put(any());
    }

    @Test
    @DisplayName("캐시 미스면 최신월을 한 번 읽어 접고 캐시에 담는다")
    void computesAndStoresSnapshotOnCacheMiss() {
        givenMonthRows(List.of(statistics("46110", "01", 100L, 1_000L)));

        service.getRegionRank(MOKPO);

        then(regionJobStatisticsRepository).should().findAllByMonth(MONTH);
        then(nonCapitalRatioCache).should().put(any());
    }

    @Test
    @DisplayName("통계가 적재되지 않았으면 백분위 맵이 비어 있다")
    void returnsEmptyPercentilesWhenNoStatistics() {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.empty());

        assertThat(service.getNonCapitalPercentiles()).isEmpty();
        assertThat(service.getRegionRank(MOKPO)).isEmpty();
    }

    @Test
    @DisplayName("점수 정규화용 백분위 맵에는 비수도권만 담긴다")
    void exposesPercentilesForNonCapitalOnly() {
        givenMonthRows(List.of(
                statistics("11680", "01", 500L, 1_000L),
                statistics("46110", "01", 100L, 1_000L),
                statistics("46130", "01", 300L, 1_000L)));

        Map<SigunguCode, Integer> percentiles = service.getNonCapitalPercentiles();

        assertThat(percentiles).containsOnlyKeys(MOKPO, SigunguCode.of("46130"));
        assertThat(percentiles.get(SigunguCode.of("46130")))
                .isGreaterThan(percentiles.get(MOKPO));
    }

    private void givenMonthRows(List<RegionJobStatistics> rows) {
        given(regionJobStatisticsRepository.findLatestMonth()).willReturn(Optional.of(MONTH));
        given(nonCapitalRatioCache.find(MONTH)).willReturn(Optional.empty());
        given(regionJobStatisticsRepository.findAllByMonth(MONTH)).willReturn(rows);
    }

    private RegionJobStatistics statistics(String sigunguCode, String jobTopCode,
                                           long validOpenings, long validSeekers) {
        return RegionJobStatistics.of(
                new RegionJobStatisticsKey(SigunguCode.of(sigunguCode), JobCode.of(jobTopCode), MONTH),
                0L, 0L, 0L, validOpenings, validSeekers);
    }
}
