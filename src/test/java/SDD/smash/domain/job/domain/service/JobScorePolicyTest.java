package SDD.smash.domain.job.domain.service;

import SDD.smash.domain.job.domain.model.RegionJobCount;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 일자리 점수 산식. 구인배수 백분위를 섞기 전/후를 함께 확인한다. */
class JobScorePolicyTest {

    private static final SigunguCode A = SigunguCode.of("46110");
    private static final SigunguCode B = SigunguCode.of("46130");

    private final JobScorePolicy policy = new JobScorePolicy();

    @Test
    @DisplayName("일자리 수가 가장 많은 지역이 100점이고 나머지는 비율 환산이다")
    void scalesByMaximumJobCount() {
        Map<SigunguCode, Score> scores = policy.scores(List.of(
                new RegionJobCount(A, 1_000L),
                new RegionJobCount(B, 500L)));

        assertThat(scores.get(A)).isEqualTo(Score.of(100));
        assertThat(scores.get(B)).isEqualTo(Score.of(50));
    }

    @Test
    @DisplayName("백분위를 주지 않으면 일자리 수만 보던 점수와 같다")
    void keepsCountOnlyScoresWhenNoPercentiles() {
        List<RegionJobCount> counts = List.of(new RegionJobCount(A, 1_000L), new RegionJobCount(B, 500L));

        assertThat(policy.scores(counts, Map.of())).isEqualTo(policy.scores(counts));
        assertThat(policy.scores(counts, null)).isEqualTo(policy.scores(counts));
    }

    @Test
    @DisplayName("구인배수 백분위는 8:2 가중으로 섞인다")
    void blendsPercentileWithConservativeWeight() {
        // given - A 는 일자리 수 100점 / 백분위 0, B 는 50점 / 백분위 100
        Map<SigunguCode, Score> scores = policy.scores(
                List.of(new RegionJobCount(A, 1_000L), new RegionJobCount(B, 500L)),
                Map.of(A, 0, B, 100));

        // then - 0.8*100 + 0.2*0 = 80 / 0.8*50 + 0.2*100 = 60
        assertThat(scores.get(A)).isEqualTo(Score.of(80));
        assertThat(scores.get(B)).isEqualTo(Score.of(60));
    }

    @Test
    @DisplayName("백분위가 보조라 일자리 수 순위를 뒤집으려면 격차가 커야 한다")
    void keepsJobCountAsPrimarySignal() {
        // given - 일자리 수 20점 차이는 백분위 100 차이로도 뒤집히지 않는다
        Map<SigunguCode, Score> scores = policy.scores(
                List.of(new RegionJobCount(A, 1_000L), new RegionJobCount(B, 800L)),
                Map.of(A, 0, B, 100));

        // then - 0.8*100 = 80 vs 0.8*80 + 20 = 84 -> 뒤집힌다(20점 차이가 경계다)
        assertThat(scores.get(B).value()).isGreaterThan(scores.get(A).value());

        // 25점 차이면 유지된다
        Map<SigunguCode, Score> wider = policy.scores(
                List.of(new RegionJobCount(A, 1_000L), new RegionJobCount(B, 700L)),
                Map.of(A, 0, B, 100));
        assertThat(wider.get(A).value()).isGreaterThan(wider.get(B).value());
    }

    @Test
    @DisplayName("백분위가 없는 지역(수도권 등)은 일자리 수 점수를 그대로 쓴다")
    void leavesRegionsWithoutPercentileUntouched() {
        Map<SigunguCode, Score> scores = policy.scores(
                List.of(new RegionJobCount(A, 1_000L), new RegionJobCount(B, 500L)),
                Map.of(B, 100));

        assertThat(scores.get(A)).isEqualTo(Score.of(100));
        assertThat(scores.get(B)).isEqualTo(Score.of(60));
    }

    @Test
    @DisplayName("일자리 수 원본이 비어 있어도 백분위만으로 점수가 만들어진다")
    void scoresFromPercentileAloneWhenCountsAreEmpty() {
        // given - JobCount 가 0행인 상태(외부 API 차단)
        Map<SigunguCode, Score> scores = policy.scores(List.of(), Map.of(A, 100, B, 50));

        // then - 0.2 * 백분위
        assertThat(scores.get(A)).isEqualTo(Score.of(20));
        assertThat(scores.get(B)).isEqualTo(Score.of(10));
    }

    @Test
    @DisplayName("일자리 수가 전부 0이어도 백분위만으로 점수가 만들어진다")
    void blendsPercentileEvenWhenEveryCountIsZero() {
        Map<SigunguCode, Score> scores = policy.scores(
                List.of(new RegionJobCount(A, 0L), new RegionJobCount(B, 0L)),
                Map.of(A, 100, B, 0));

        assertThat(scores.get(A)).isEqualTo(Score.of(20));
        assertThat(scores.get(B)).isEqualTo(Score.ZERO);
    }
}
