package SDD.smash.domain.job.application.dto;

/**
 * 한 시군구의 월별 고용행정통계 추세 한 점(직종 13종 합계).
 *
 * <p>추세 화면이 쓰는 세 지표만 담는다 — 나머지 지표는 이 그래프에 필요하지 않다.
 *
 * @param statisticsMonth 기준월({@code YYYY-MM})
 * @param jobOpeningRatio 구인배수. 그 달 유효구직자수 합계가 0 이면 {@code null}
 */
public record RegionJobStatisticsTrendPoint(String statisticsMonth,
                                            long validOpenings,
                                            long validSeekers,
                                            Double jobOpeningRatio) {
}
