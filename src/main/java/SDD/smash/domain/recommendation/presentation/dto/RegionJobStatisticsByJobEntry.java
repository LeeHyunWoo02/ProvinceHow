package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobItem;

/**
 * 직종 대분류별 고용행정통계 한 종. HTTP 응답 계약(JSON 필드명)의 소유자다.
 * 지표 필드는 상세 요약({@link RegionJobStatisticsEntry})과 동일하고 직종 식별자만 앞에 붙는다.
 *
 * @param jobOpeningRatio 구인배수. 유효구직자수가 0 이면 {@code null}
 */
public record RegionJobStatisticsByJobEntry(String jobMajorCode,
                                            String jobMajorName,
                                            long validOpenings,
                                            long validSeekers,
                                            Double jobOpeningRatio,
                                            long jobOpenings,
                                            long jobSeekers,
                                            long placements) {

    public static RegionJobStatisticsByJobEntry from(RegionJobStatisticsByJobItem item) {
        return new RegionJobStatisticsByJobEntry(
                item.jobMajorCode(),
                item.jobMajorName(),
                item.validOpenings(),
                item.validSeekers(),
                item.jobOpeningRatio(),
                item.jobOpenings(),
                item.jobSeekers(),
                item.placements());
    }
}
