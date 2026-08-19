package SDD.smash.domain.support.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수집 결과 값 객체. "수집 실패"와 "정말 0건"이 값으로 구분되는지만 본다(모킹 없음).
 */
class SupportPolicyCollectionTest {

    private static final SupportPolicy POLICY = new SupportPolicy("청년월세지원", "https://example.test", "주거지원");

    @Test
    @DisplayName("수집 실패와 0건 수집은 정책 수가 같아도 서로 다른 결과다")
    void distinguishesFailureFromEmptyResult() {
        // given
        SupportPolicyCollection failure = SupportPolicyCollection.notCollected();
        SupportPolicyCollection empty = SupportPolicyCollection.of(List.of());

        // then
        assertThat(failure.collected()).isFalse();
        assertThat(empty.collected()).isTrue();
        assertThat(failure.size()).isEqualTo(empty.size()).isZero();
        assertThat(failure).isNotEqualTo(empty);
    }

    @Test
    @DisplayName("수집한 정책은 그대로 담기고 개수를 센다")
    void keepsCollectedPolicies() {
        // when
        SupportPolicyCollection collection = SupportPolicyCollection.of(List.of(POLICY, POLICY));

        // then
        assertThat(collection.collected()).isTrue();
        assertThat(collection.size()).isEqualTo(2);
        assertThat(collection.policies()).containsExactly(POLICY, POLICY);
    }

    @Test
    @DisplayName("수집하지 못한 결과는 정책을 담을 수 없다")
    void notCollectedNeverCarriesPolicies() {
        // when — 생성자를 직접 불러도 불변식이 강제된다
        SupportPolicyCollection collection = new SupportPolicyCollection(false, List.of(POLICY));

        // then
        assertThat(collection.policies()).isEmpty();
    }

    @Test
    @DisplayName("전달한 목록을 나중에 바꿔도 수집 결과는 흔들리지 않는다")
    void copiesGivenPolicies() {
        // given
        List<SupportPolicy> source = new ArrayList<>(List.of(POLICY));
        SupportPolicyCollection collection = SupportPolicyCollection.of(source);

        // when
        source.clear();

        // then
        assertThat(collection.size()).isEqualTo(1);
    }
}
