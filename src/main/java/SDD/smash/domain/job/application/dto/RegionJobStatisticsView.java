package SDD.smash.domain.job.application.dto;

import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.global.domain.model.SigunguCode;

/**
 * 고용행정통계 조회 결과.
 *
 * @param statisticsMonth 기준월({@code YYYY-MM}). 화면이 "2026년 7월 기준" 을 표시해야 하므로
 *                        <b>반드시 함께 내려보낸다</b> — 빠지면 낡은 수치가 현재값으로 보인다
 * @param jobOpeningRatio 구인배수. 유효구직자수가 0 이라 계산할 수 없으면 {@code null}
 */
public record RegionJobStatisticsView(
        SigunguCode sigunguCode,
        String jobTopCode,
        String statisticsMonth,
        long jobOpenings,
        long jobSeekers,
        long placements,
        long validOpenings,
        long validSeekers,
        Double jobOpeningRatio) {

    public static RegionJobStatisticsView from(RegionJobStatistics statistics) {
        return new RegionJobStatisticsView(
                statistics.sigunguCode(),
                statistics.jobCode().value(),
                statistics.month().text(),
                statistics.jobOpenings(),
                statistics.jobSeekers(),
                statistics.placements(),
                statistics.validOpenings(),
                statistics.validSeekers(),
                statistics.jobOpeningRatio().orElse(null));
    }
}
