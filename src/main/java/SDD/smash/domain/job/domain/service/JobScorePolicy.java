package SDD.smash.domain.job.domain.service;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.RegionJobCount;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 일자리 수를 지역 간 상대 비교해 적합도를 계산한다.
 *
 * <p>"일자리가 가장 많은 지역을 100점으로 두고 나머지를 그 비율로 환산한다"는 도메인 지식이다.
 * 저장소·캐시에 의존하지 않는 순수 함수다.
 *
 * <p>As-Is {@code JobScoreService} 의 최대값 탐색 + 백분율 구간을 그대로 옮겼다.
 * 입력 순서를 유지해야 하므로 {@link LinkedHashMap} 으로 돌려준다.
 */
public class JobScorePolicy {

    private static final int FULL_SCORE = 100;

    /**
     * @param counts 지역별 일자리 수. 비어 있으면 결과도 비어 있다
     * @return 지역별 0~100 점수
     */
    public Map<SigunguCode, Score> scores(List<RegionJobCount> counts) {

        long maxCount = 0L;
        for (RegionJobCount count : counts) {
            if (count.count() > maxCount) {
                maxCount = count.count();
            }
        }

        Map<SigunguCode, Score> scores = new LinkedHashMap<>();

        // 데이터는 있는데 전부 0인 경우 → 전부 0점. 0 으로 나누는 것을 피하는 분기이기도 하다.
        if (maxCount == 0) {
            for (RegionJobCount count : counts) {
                scores.put(count.sigunguCode(), Score.ZERO);
            }
            return scores;
        }

        for (RegionJobCount count : counts) {
            int score = (int) Math.floor((count.count() * (double) FULL_SCORE) / maxCount);
            scores.put(count.sigunguCode(), Score.of(score));
        }
        return scores;
    }
}
