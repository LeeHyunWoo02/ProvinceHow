package SDD.smash.domain.job.infrastructure.batch.dto;

/**
 * EIS 고용행정통계 시드 CSV 한 줄.
 *
 * <p>파일에는 {@code sido_code}/{@code sigungu_name} 도 있지만 사람이 눈으로 확인하기 위한 컬럼이라
 * 적재하지 않는다.
 */
public record RegionJobStatisticsCsvRow(
        String sigunguCode,
        String jobTopCode,
        String yearMonth,
        Long jobOpenings,
        Long jobSeekers,
        Long placements,
        Long validOpenings,
        Long validSeekers) {
}
