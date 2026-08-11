package SDD.smash.recommendation.domain.service;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SidoCode;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.recommendation.domain.model.RegionScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이관 안전망. As-Is {@code Apis.Service.RecommendService.recommend} 의 조합 공식을
 * <b>이 테스트 안에 그대로 재현</b>해 {@code RegionScorePolicy} 와 대조한다.
 *
 * <p>다른 컨텍스트들과 달리 여기서는 "옛 클래스를 직접 호출해 대조"할 수 없다 —
 * {@code recommendation} 이관이 곧 {@code Apis.Service.RecommendService} 삭제이므로,
 * 대조 대상이 되어줄 살아있는 옛 구현이 이 시점엔 존재하지 않는다(참조 0을 확인하고 지웠다).
 * 그래서 As-Is 소스에 있던 정수 연산을 이 테스트의 {@code legacyCombine}/{@code legacyTop10}
 * 메서드로 문자 그대로 재현해 "문서화된 As-Is 동작"과 대조하는 안전망으로 삼는다.
 *
 * <p>재현 대상 As-Is 코드(주석으로 고정):
 * <pre>
 * int div = 4;
 * if (supportChoice == null || supportChoice == 0) div--;
 * if (infraChoice == null || infraChoice == 0) div--;
 * int sum = jobScore + dwellingScore + supportScore + infraScore;
 * score = sum / div;                                   // 정수 나눗셈
 * scores.sort((a,b) -&gt; b.getScore().compareTo(a.getScore()));
 * top10 = scores.size() &gt; 10 ? scores.subList(0,10) : scores;
 * maxScore = top10.get(0).getScore();
 * finalScore = (int) Math.round(((double) score / maxScore) * 100);
 * </pre>
 */
class RegionScorePolicyParityTest {

    private final RegionScorePolicy policy = new RegionScorePolicy();

    private int legacyCombine(int job, int dwelling, int support, int infra,
                              Integer supportChoice, Integer infraChoice) {
        int div = 4;
        if (supportChoice == null || supportChoice == 0) div--;
        if (infraChoice == null || infraChoice == 0) div--;
        int sum = job + dwelling + support + infra;
        return sum / div;
    }

    private List<Integer> legacyTop10Renormalized(List<Integer> rawScores) {
        List<Integer> sorted = new ArrayList<>(rawScores);
        sorted.sort((a, b) -> b.compareTo(a));
        List<Integer> top10 = sorted.size() > 10 ? sorted.subList(0, 10) : sorted;
        int maxScore = top10.get(0);
        List<Integer> result = new ArrayList<>();
        for (Integer score : top10) {
            result.add((int) Math.round(((double) score / maxScore) * 100));
        }
        return result;
    }

    @Test
    @DisplayName("네 선택 조합(전부/일부/전무 선택)에서 combine 결과가 As-Is 공식과 같다")
    void combineMatchesLegacyFormulaForEverySelectionCombo() {
        Integer[] supportChoices = {null, 0, 1, 15};
        Integer[] infraChoices = {null, 0, 1, 15};

        for (int job = 0; job <= 100; job += 25) {
            for (int dwelling = 0; dwelling <= 100; dwelling += 25) {
                for (Integer supportChoice : supportChoices) {
                    for (Integer infraChoice : infraChoices) {
                        boolean supportSelected = supportChoice != null && supportChoice != 0;
                        boolean infraSelected = infraChoice != null && infraChoice != 0;
                        // As-Is 는 선택 안 한 항목의 점수 맵이 항상 빈 맵이라 getOrDefault(0)이 되므로
                        // 원시 점수도 0으로 맞춰 그 특성을 그대로 재현한다.
                        int support = supportSelected ? 60 : 0;
                        int infra = infraSelected ? 40 : 0;

                        int expected = legacyCombine(job, dwelling, support, infra, supportChoice, infraChoice);

                        Score combined = policy.combine(Score.of(job), Score.of(dwelling),
                                Score.of(support), Score.of(infra), supportSelected, infraSelected);

                        assertThat(combined.value())
                                .as("job=%d, dwelling=%d, supportChoice=%s, infraChoice=%s", job, dwelling, supportChoice, infraChoice)
                                .isEqualTo(expected);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("상위 10개 재정규화가 As-Is 공식과 같다(동점 순서 포함)")
    void selectTopTenRenormalizedMatchesLegacyFormula() {
        List<Integer> rawScores = List.of(80, 80, 60, 100, 40, 20, 0, 55, 55, 30, 10, 5);

        List<Integer> expected = legacyTop10Renormalized(rawScores);

        List<RegionScore> candidates = new ArrayList<>();
        for (int i = 0; i < rawScores.size(); i++) {
            candidates.add(new RegionScore(
                    SigunguCode.of(String.format("%05d", 11000 + i)),
                    SidoCode.of("30"),
                    Score.of(rawScores.get(i))));
        }

        List<RegionScore> actual = policy.selectTopTenRenormalized(candidates);

        assertThat(actual.stream().map(r -> r.score().value()).toList()).isEqualTo(expected);
    }

    @Test
    @DisplayName("전원 0점이면 재정규화도 전원 0점이다(0/0 은 NaN이고 Math.round(NaN)은 0)")
    void allZeroScoresRenormalizeToAllZero() {
        List<RegionScore> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            candidates.add(new RegionScore(
                    SigunguCode.of(String.format("%05d", 11000 + i)),
                    SidoCode.of("30"),
                    Score.ZERO));
        }

        List<RegionScore> actual = policy.selectTopTenRenormalized(candidates);

        assertThat(actual).allSatisfy(r -> assertThat(r.score()).isEqualTo(Score.ZERO));
    }

    @Test
    @DisplayName("서울(11)·경기(41)·인천(28)만 제외 대상이다")
    void onlyExcludesSeoulGyeonggiIncheon() {
        assertThat(policy.isExcluded(SidoCode.of("11"))).isTrue();
        assertThat(policy.isExcluded(SidoCode.of("41"))).isTrue();
        assertThat(policy.isExcluded(SidoCode.of("28"))).isTrue();
        assertThat(policy.isExcluded(SidoCode.of("30"))).isFalse();
        assertThat(policy.isExcluded(SidoCode.of("48"))).isFalse();
    }
}
