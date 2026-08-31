package SDD.smash.domain.job.application;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobScoreKey;
import SDD.smash.domain.job.domain.model.RegionJobCount;
import SDD.smash.domain.job.domain.port.JobCategoryRepository;
import SDD.smash.domain.job.domain.port.JobCountRepository;
import SDD.smash.domain.job.domain.port.JobScoreCache;
import SDD.smash.global.domain.model.Score;
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
class JobScoreServiceTest {

    private static final SigunguCode A = SigunguCode.of("46110");
    private static final SigunguCode B = SigunguCode.of("46130");

    @Mock JobCountRepository jobCountRepository;
    @Mock JobCategoryRepository jobCategoryRepository;
    @Mock JobScoreCache jobScoreCache;
    @Mock NonCapitalJobRankingService nonCapitalJobRankingService;

    @InjectMocks JobScoreService service;

    @Test
    @DisplayName("일자리 수 점수에 비수도권 구인배수 백분위가 섞인다")
    void blendsNonCapitalPercentileIntoJobScore() {
        // given - A 는 일자리 수 최대이지만 구인배수 백분위는 최하위다
        given(jobScoreCache.find(any())).willReturn(Optional.empty());
        given(jobCountRepository.findAllRegionTotals()).willReturn(List.of(
                new RegionJobCount(A, 1_000L), new RegionJobCount(B, 500L)));
        given(nonCapitalJobRankingService.getNonCapitalPercentiles()).willReturn(Map.of(A, 0, B, 100));

        // when
        Map<SigunguCode, Score> scores = service.scoresFor(null);

        // then - 0.8*100 = 80 / 0.8*50 + 0.2*100 = 60
        assertThat(scores.get(A)).isEqualTo(Score.of(80));
        assertThat(scores.get(B)).isEqualTo(Score.of(60));
        then(jobScoreCache).should().put(JobScoreKey.all(), scores);
    }

    @Test
    @DisplayName("통계가 없어 백분위가 비면 일자리 수만 보던 점수가 그대로 나온다")
    void fallsBackToCountOnlyScoreWhenNoStatistics() {
        given(jobScoreCache.find(any())).willReturn(Optional.empty());
        given(jobCountRepository.findAllRegionTotals()).willReturn(List.of(
                new RegionJobCount(A, 1_000L), new RegionJobCount(B, 500L)));
        given(nonCapitalJobRankingService.getNonCapitalPercentiles()).willReturn(Map.of());

        Map<SigunguCode, Score> scores = service.scoresFor(null);

        assertThat(scores.get(A)).isEqualTo(Score.of(100));
        assertThat(scores.get(B)).isEqualTo(Score.of(50));
    }

    @Test
    @DisplayName("캐시가 있으면 통계도 일자리 수도 읽지 않는다")
    void returnsCachedScoresWithoutRecomputing() {
        given(jobScoreCache.find(JobScoreKey.of(JobCode.of("011")))).willReturn(
                Optional.of(Map.of(A, Score.of(70))));
        given(jobCategoryRepository.existsSubCategory(JobCode.of("011"))).willReturn(true);

        Map<SigunguCode, Score> scores = service.scoresFor(JobCode.of("011"));

        assertThat(scores).containsEntry(A, Score.of(70));
        then(jobCountRepository).shouldHaveNoInteractions();
        then(nonCapitalJobRankingService).should(never()).getNonCapitalPercentiles();
    }
}
