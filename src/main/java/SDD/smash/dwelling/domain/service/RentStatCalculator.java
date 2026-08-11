package SDD.smash.dwelling.domain.service;

import java.util.List;
import java.util.Objects;

/**
 * 실거래 값 목록에서 평균·중앙값을 구한다.
 *
 * <p>As-Is {@code SDD.smash.Util.CalculateUtil} 을 옮긴 것이다.
 * 전월세 통계 산출은 기술 유틸이 아니라 도메인 계산이므로 dwelling 도메인에 둔다
 * (global-conventions §6).
 *
 * <p>값이 하나도 없으면 {@code null} 을 돌려준다. "실거래 없음"은 정상 상태다.
 * 만원 단위 원시 값을 다루므로 여기서는 {@code Money} 로 승격하지 않는다
 * — 승격은 저장소에서 읽어 도메인 모델을 복원할 때 한다.
 */
public final class RentStatCalculator {

    private RentStatCalculator() {
    }

    /** 평균. 소수점 첫째 자리로 반올림한다. null 원소는 건너뛴다. */
    public static Double mean(List<Integer> values) {
        if (values == null || values.isEmpty()) return null;
        long sum = 0;
        int count = 0;
        for (Integer value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        if (count == 0) return null;

        double avg = sum * 1.0 / count;
        return Math.round(avg * 10.0) / 10.0;
    }

    /** 중앙값. 짝수 개면 가운데 두 값의 평균을 반올림한다. null 원소는 걸러낸다. */
    public static Integer median(List<Integer> values) {
        if (values == null) return null;
        List<Integer> sorted = values.stream().filter(Objects::nonNull).sorted().toList();
        int n = sorted.size();
        if (n == 0) return null;
        if ((n & 1) == 1) return sorted.get(n / 2);
        return (int) Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
    }
}
