package SDD.smash.domain.support.domain.service;

import SDD.smash.global.domain.model.Score;
import SDD.smash.domain.support.domain.model.SupportTag;

import java.util.Map;
import java.util.Set;

/**
 * 한 시군구에서 선택한 태그들의 지원정책 개수로 적합도를 계산한다.
 *
 * <p>"정책이 하나라도 있으면 그 태그는 만점, 없으면 0점, 선택한 태그 수로 평균 낸다"는
 * 도메인 지식이다. 저장소·캐시에 의존하지 않는 순수 함수다.
 *
 * <p>As-Is {@code SupportScoreService.getSupportScoresByTag} 의 계산 부분을 그대로 옮겼다.
 * {@code count / selectedTags.size()} 는 정수 나눗셈이라 소수점이 버려지는 것까지 As-Is 그대로다.
 */
public class SupportScorePolicy {

    private static final int FULL_SCORE = 100;

    /**
     * @param countsByTag 이 시군구에서 선택한 태그별 정책 개수. 개수가 없는 태그는 맵에서 빠져 있을 수 있다
     * @param selectedTags 사용자가 고른 태그 집합. 비어 있지 않아야 한다(호출부가 미리 걸러낸다)
     */
    public Score score(Map<SupportTag, Integer> countsByTag, Set<SupportTag> selectedTags) {
        int sum = 0;
        for (SupportTag tag : selectedTags) {
            Integer count = countsByTag.get(tag);
            sum += (count != null && count > 0) ? FULL_SCORE : 0;
        }
        return Score.of(sum / selectedTags.size());
    }
}
