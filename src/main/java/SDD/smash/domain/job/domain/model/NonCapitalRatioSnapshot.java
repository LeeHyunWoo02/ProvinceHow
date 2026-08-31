package SDD.smash.domain.job.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 한 기준월의 <b>비수도권</b> 구인배수 분포.
 *
 * <p>백분위는 한 지역의 값만으로 계산할 수 없고 모집단 전체가 필요하다. 요청마다 최신월
 * 전국(3,432행)을 다시 읽지 않도록 이 스냅샷을 한 번 만들어 캐시에 담는다.
 *
 * <p>배수를 계산할 수 없는 지역(유효구직자수 0)은 분포에 넣지 않는다. 0 으로 채우면
 * "구인이 없는 지역" 과 "구직자가 없는 지역" 이 같은 값으로 뭉개진다.
 */
public class NonCapitalRatioSnapshot {

    private final StatisticsMonth month;

    /** 시군구 -> 직종 13종 합계 구인배수 */
    private final Map<SigunguCode, Double> totalRatios;

    /** 직종 대분류 -> (시군구 -> 그 직종의 구인배수) */
    private final Map<JobCode, Map<SigunguCode, Double>> ratiosByJob;

    private NonCapitalRatioSnapshot(StatisticsMonth month,
                                    Map<SigunguCode, Double> totalRatios,
                                    Map<JobCode, Map<SigunguCode, Double>> ratiosByJob) {
        if (month == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "분포의 기준월은 필수입니다.");
        }
        this.month = month;
        this.totalRatios = Collections.unmodifiableMap(new LinkedHashMap<>(totalRatios));

        Map<JobCode, Map<SigunguCode, Double>> copied = new LinkedHashMap<>();
        ratiosByJob.forEach((jobCode, ratios) ->
                copied.put(jobCode, Collections.unmodifiableMap(new LinkedHashMap<>(ratios))));
        this.ratiosByJob = Collections.unmodifiableMap(copied);
    }

    public static NonCapitalRatioSnapshot of(StatisticsMonth month,
                                             Map<SigunguCode, Double> totalRatios,
                                             Map<JobCode, Map<SigunguCode, Double>> ratiosByJob) {
        return new NonCapitalRatioSnapshot(month, totalRatios, ratiosByJob);
    }

    /** 비어 있는 분포. 통계가 적재되지 않은 상태를 표현한다. */
    public static NonCapitalRatioSnapshot empty(StatisticsMonth month) {
        return new NonCapitalRatioSnapshot(month, Map.of(), Map.of());
    }

    public StatisticsMonth month() {
        return month;
    }

    /** 직종 13종 합계 기준 분포 */
    public Map<SigunguCode, Double> totalRatios() {
        return totalRatios;
    }

    /** 해당 직종 대분류의 분포. {@code null} 이면 합계 분포이고, 그 직종 행이 없으면 빈 맵이다. */
    public Map<SigunguCode, Double> ratiosOf(JobCode jobCode) {
        if (jobCode == null) {
            return totalRatios;
        }
        return ratiosByJob.getOrDefault(jobCode, Map.of());
    }

    /** 분포가 담고 있는 직종 대분류들. 적재된 직종만 나온다. */
    public Set<JobCode> jobCodes() {
        return ratiosByJob.keySet();
    }

    public boolean isEmpty() {
        return totalRatios.isEmpty();
    }
}
