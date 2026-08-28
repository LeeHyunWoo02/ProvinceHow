package SDD.smash.domain.job.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.util.Optional;

/**
 * 시군구 × 직종 대분류 × 기준월의 고용행정통계 (Aggregate Root).
 *
 * <p>출처는 EIS 고용행정통계다. {@code JobCount}(개별 공고 집계) 와 차원(월)과 지표(5종)가
 * 맞지 않아 별도 Aggregate 로 둔다 — 한 소스가 끊겨도 다른 쪽이 흔들리지 않게 하는 것이 목적이다.
 */
public class RegionJobStatistics {

    private final RegionJobStatisticsKey key;

    /** 구인인원(월). 그 달에 새로 올라온 구인 규모다. */
    private final long jobOpenings;
    /** 구직건수(월) */
    private final long jobSeekers;
    /** 취업건수(월) */
    private final long placements;
    /** 유효구인인원. 주 지표다. */
    private final long validOpenings;
    /** 유효구직자수 */
    private final long validSeekers;

    private RegionJobStatistics(RegionJobStatisticsKey key, long jobOpenings, long jobSeekers,
                                long placements, long validOpenings, long validSeekers) {
        if (key == null) {
            throw new DomainException(ErrorCode.JOB_STATISTICS_INVALID, "통계 키는 필수입니다.");
        }
        requireNotNegative(jobOpenings, "구인인원");
        requireNotNegative(jobSeekers, "구직건수");
        requireNotNegative(placements, "취업건수");
        requireNotNegative(validOpenings, "유효구인인원");
        requireNotNegative(validSeekers, "유효구직자수");

        this.key = key;
        this.jobOpenings = jobOpenings;
        this.jobSeekers = jobSeekers;
        this.placements = placements;
        this.validOpenings = validOpenings;
        this.validSeekers = validSeekers;
    }

    public static RegionJobStatistics of(RegionJobStatisticsKey key, long jobOpenings, long jobSeekers,
                                         long placements, long validOpenings, long validSeekers) {
        return new RegionJobStatistics(key, jobOpenings, jobSeekers, placements, validOpenings, validSeekers);
    }

    /** 재구성용 — 저장소 어댑터가 사용한다. */
    public static RegionJobStatistics reconstitute(RegionJobStatisticsKey key, long jobOpenings, long jobSeekers,
                                                   long placements, long validOpenings, long validSeekers) {
        return new RegionJobStatistics(key, jobOpenings, jobSeekers, placements, validOpenings, validSeekers);
    }

    /**
     * 구인배수 = 유효구인인원 / 유효구직자수. 구직자 한 명당 일자리가 몇 개인지를 뜻한다.
     *
     * <p>구직자가 0 이면 배수가 성립하지 않는다. 0 이나 무한대로 채우면 "구인이 없는 지역" 과
     * "구직자가 없는 지역" 이 같은 값으로 뭉개지므로 <b>값 없음</b>으로 돌려준다.
     */
    public Optional<Double> jobOpeningRatio() {
        if (validSeekers == 0L) {
            return Optional.empty();
        }
        return Optional.of((double) validOpenings / (double) validSeekers);
    }

    private static void requireNotNegative(long value, String name) {
        if (value < 0L) {
            throw new DomainException(ErrorCode.JOB_STATISTICS_INVALID, name + "은(는) 0 이상이어야 합니다.");
        }
    }

    public RegionJobStatisticsKey key()   { return key; }
    public SigunguCode sigunguCode()      { return key.sigunguCode(); }
    public JobCode jobCode()              { return key.jobCode(); }
    public StatisticsMonth month()        { return key.month(); }
    public long jobOpenings()             { return jobOpenings; }
    public long jobSeekers()              { return jobSeekers; }
    public long placements()              { return placements; }
    public long validOpenings()           { return validOpenings; }
    public long validSeekers()            { return validSeekers; }
}
