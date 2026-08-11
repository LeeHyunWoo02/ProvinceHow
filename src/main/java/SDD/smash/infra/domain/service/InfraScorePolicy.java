package SDD.smash.infra.domain.service;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.infra.domain.model.Major;
import SDD.smash.infra.domain.model.RegionMajorScore;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 사용자가 고른 인프라 대분류들의 평균 점수를 시군구별로 합산해 적합도를 계산한다.
 *
 * <p>"선택한 대분류들의 평균 점수를 다시 평균 내면 적합도가 된다"는 도메인 지식이다.
 * 저장소·캐시에 의존하지 않는 순수 함수다.
 *
 * <p>As-Is {@code InfraScoreService.getInfraScoresByChoice} 의 계산 부분을 그대로 옮겼다.
 * 대분류를 하나도 고르지 않았으면(선택 없음) 빈 맵을 돌려준다 — 이 경우 추천 로직이
 * 자동으로 0점 처리하도록 As-Is 가 설계돼 있어 그대로 유지한다.
 */
public class InfraScorePolicy {

    public Map<SigunguCode, Score> scores(Set<Major> selectedMajors, java.util.List<RegionMajorScore> regionScores) {

        if (selectedMajors.isEmpty()) {
            return Map.of();
        }

        // merge(key, value, accumulator): 시군구는 같고 대분류가 다른 행들의 점수를 더한다.
        Map<SigunguCode, Double> sumBySigungu = new HashMap<>();
        for (RegionMajorScore row : regionScores) {
            double toAdd = row.averageScore() == null ? 0.0 : row.averageScore();
            sumBySigungu.merge(row.sigunguCode(), toAdd, Double::sum);
        }

        int divisor = selectedMajors.size();
        Map<SigunguCode, Score> result = new LinkedHashMap<>();
        for (Map.Entry<SigunguCode, Double> entry : sumBySigungu.entrySet()) {
            result.put(entry.getKey(), Score.of((int) Math.round(entry.getValue() / divisor)));
        }
        return result;
    }
}
