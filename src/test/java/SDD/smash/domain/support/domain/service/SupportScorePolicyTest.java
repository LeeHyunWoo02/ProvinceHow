package SDD.smash.domain.support.domain.service;

import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.global.domain.model.Score;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지원정책 적합도 정책. 모킹 없이 순수 계산만 본다(backend-conventions §7.3).
 * "정책이 하나라도 있으면 만점, 없으면 0점, 선택 태그 수로 평균"과 정수 나눗셈 경계를
 * {@code Score} 값까지 단언한다.
 */
class SupportScorePolicyTest {

    private final SupportScorePolicy policy = new SupportScorePolicy();

    @Test
    @DisplayName("선택한 태그가 모두 정책을 가지면 만점이다")
    void scoresFullWhenAllSelectedTagsHavePolicies() {
        Set<SupportTag> selected = EnumSet.of(SupportTag.HOUSING_SUPPORT, SupportTag.LOAN);
        Map<SupportTag, Integer> counts = Map.of(
                SupportTag.HOUSING_SUPPORT, 3,
                SupportTag.LOAN, 1);

        assertThat(policy.score(counts, selected)).isEqualTo(Score.of(100));
    }

    @Test
    @DisplayName("선택한 태그 중 하나도 매칭되지 않으면 0점이다")
    void scoresZeroWhenNoSelectedTagMatches() {
        Set<SupportTag> selected = EnumSet.of(SupportTag.HOUSING_SUPPORT, SupportTag.LOAN);
        Map<SupportTag, Integer> counts = Map.of(); // 개수 없음 → 전부 0점

        assertThat(policy.score(counts, selected)).isEqualTo(Score.ZERO);
    }

    @Test
    @DisplayName("정책이 하나라도 있으면 그 태그는 만점 처리된다(개수 1이면 100점 경계)")
    void treatsAnyPositiveCountAsFullForThatTag() {
        Set<SupportTag> selected = EnumSet.of(SupportTag.HOUSING_SUPPORT);
        Map<SupportTag, Integer> counts = Map.of(SupportTag.HOUSING_SUPPORT, 1);

        assertThat(policy.score(counts, selected)).isEqualTo(Score.of(100));
    }

    @Test
    @DisplayName("개수가 0이면 정책이 없는 것과 같이 0점 처리된다")
    void treatsZeroCountAsNoPolicy() {
        Set<SupportTag> selected = EnumSet.of(SupportTag.HOUSING_SUPPORT);
        Map<SupportTag, Integer> counts = new HashMap<>();
        counts.put(SupportTag.HOUSING_SUPPORT, 0);

        assertThat(policy.score(counts, selected)).isEqualTo(Score.ZERO);
    }

    @Test
    @DisplayName("두 태그 중 하나만 매칭되면 정수 나눗셈으로 50점이다")
    void averagesHalfWhenOneOfTwoTagsMatches() {
        Set<SupportTag> selected = EnumSet.of(SupportTag.HOUSING_SUPPORT, SupportTag.LOAN);
        Map<SupportTag, Integer> counts = Map.of(SupportTag.HOUSING_SUPPORT, 2);

        assertThat(policy.score(counts, selected)).isEqualTo(Score.of(50));
    }

    @Test
    @DisplayName("세 태그 중 하나만 매칭되면 100/3 의 소수점이 버려져 33점이다")
    void truncatesIntegerDivisionForOneOfThree() {
        Set<SupportTag> selected =
                EnumSet.of(SupportTag.HOUSING_SUPPORT, SupportTag.INTERN, SupportTag.LOAN);
        Map<SupportTag, Integer> counts = Map.of(SupportTag.HOUSING_SUPPORT, 1);

        assertThat(policy.score(counts, selected)).isEqualTo(Score.of(33)); // 100/3 = 33
    }

    @Test
    @DisplayName("세 태그 중 둘이 매칭되면 200/3 의 소수점이 버려져 66점이다")
    void truncatesIntegerDivisionForTwoOfThree() {
        Set<SupportTag> selected =
                EnumSet.of(SupportTag.HOUSING_SUPPORT, SupportTag.INTERN, SupportTag.LOAN);
        Map<SupportTag, Integer> counts = Map.of(
                SupportTag.HOUSING_SUPPORT, 1,
                SupportTag.INTERN, 5);

        assertThat(policy.score(counts, selected)).isEqualTo(Score.of(66)); // 200/3 = 66
    }

    @Test
    @DisplayName("맵에 없는 태그(null 개수)는 0점으로 취급된다")
    void treatsMissingTagAsZero() {
        Set<SupportTag> selected = EnumSet.of(SupportTag.HOUSING_SUPPORT, SupportTag.LOAN);
        Map<SupportTag, Integer> counts = Map.of(SupportTag.HOUSING_SUPPORT, 2); // LOAN 은 맵에 없음

        assertThat(policy.score(counts, selected)).isEqualTo(Score.of(50));
    }

    @Test
    @DisplayName("선택 태그가 비어 있으면 0으로 나눠 계산할 수 없다(호출부가 미리 걸러야 하는 전제)")
    void cannotComputeWhenNoTagSelected() {
        // 정책 javadoc 의 전제(selectedTags 비어 있지 않음) 위반 시의 실제 동작을 고정한다.
        assertThatThrownBy(() -> policy.score(Map.of(), EnumSet.noneOf(SupportTag.class)))
                .isInstanceOf(ArithmeticException.class);
    }
}
