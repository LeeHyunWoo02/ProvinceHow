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
     * 일자리 수가 차지하는 비중. 구인배수는 나머지 {@code 1 - COUNT_WEIGHT} 만 가져간다.
     *
     * <p>보수적으로 잡았다. 일자리 수는 3년 넘게 순위를 만들어 온 주 지표이고 구인배수는
     * 이번에 처음 섞는 보조 지표다. 비중을 크게 주면 규모가 아주 작은 군 지역이 배수 하나로
     * 상위에 올라온다 — 실제 이주 가능성이 아니라 통계의 분모가 작아서 생기는 순위다.
     */
    private static final double COUNT_WEIGHT = 0.8d;
    private static final double RATIO_WEIGHT = 1.0d - COUNT_WEIGHT;

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

    /**
     * 일자리 수 점수에 <b>비수도권 내 구인배수 백분위</b>를 보조로 섞는다.
     *
     * <p>원시 구인배수를 그대로 넣지 않는다. 배수는 최소 0.024 ~ 최대 0.903 으로 분포가
     * 넓고 한쪽으로 쏠려 있어, 그대로 쓰면 이상치 몇 곳이 척도를 독차지한다. 비수도권 안의
     * 백분위(0~100)로 바꿔 넣으면 일자리 수 점수와 같은 눈금이 되어 가중합이 성립한다.
     *
     * <p>결과에는 <b>일자리 수가 없고 백분위만 있는 지역도 들어간다</b>. 일자리 수 원본이
     * 비어 있는 상태(외부 API 가 막혀 {@code JobCount} 가 0행)에서도 구인배수가 점수에
     * 반영되게 하려는 것이다. 그런 지역의 일자리 수 점수는 0 으로 본다 — 호출부가 이미
     * "맵에 없으면 0점" 으로 다루므로 의미가 달라지지 않는다.
     *
     * @param nonCapitalPercentiles 시군구 -> 비수도권 내 백분위(0~100).
     *                              여기에 없는 지역(수도권·배수 없음)은 일자리 수 점수를 그대로 쓴다
     */
    public Map<SigunguCode, Score> scores(List<RegionJobCount> counts,
                                          Map<SigunguCode, Integer> nonCapitalPercentiles) {

        Map<SigunguCode, Score> countScores = scores(counts);
        if (nonCapitalPercentiles == null || nonCapitalPercentiles.isEmpty()) {
            return countScores;
        }

        Map<SigunguCode, Score> blended = new LinkedHashMap<>();
        countScores.forEach((code, countScore) ->
                blended.put(code, blend(countScore, nonCapitalPercentiles.get(code))));

        nonCapitalPercentiles.forEach((code, percentile) -> {
            if (!blended.containsKey(code)) {
                blended.put(code, blend(Score.ZERO, percentile));
            }
        });
        return blended;
    }

    private Score blend(Score countScore, Integer percentile) {
        if (percentile == null) {
            return countScore;
        }
        int value = (int) Math.round(COUNT_WEIGHT * countScore.value() + RATIO_WEIGHT * percentile);
        return Score.of(Math.max(0, Math.min(FULL_SCORE, value)));
    }
}
