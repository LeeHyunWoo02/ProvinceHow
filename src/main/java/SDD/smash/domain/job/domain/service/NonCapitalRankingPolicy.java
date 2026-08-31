package SDD.smash.domain.job.domain.service;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.NonCapitalRank;
import SDD.smash.domain.job.domain.model.NonCapitalRatioSnapshot;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 구인배수를 <b>비수도권 시군구 안에서만</b> 상대 비교해 순위·백분위를 만든다.
 *
 * <p>이 서비스는 청년 지방이주가 목적이므로 비교 기준선은 전국이 아니라 비수도권이다.
 * 전국으로 재면 수도권 이상치가 척도를 끌어올려 비수도권 안의 차이가 뭉개진다.
 *
 * <p>저장소·시간·랜덤에 의존하지 않는 순수 함수다.
 */
public class NonCapitalRankingPolicy {

    /**
     * 수도권 시도 코드. 서울(11)·경기(41)·인천(28)이다.
     * "어디까지가 수도권인가" 는 데이터가 아니라 도메인 지식이라 정책이 들고 있는다.
     */
    private static final Set<SidoCode> CAPITAL_AREA_SIDO_CODES = Set.of(
            SidoCode.of("11"), SidoCode.of("41"), SidoCode.of("28"));

    private static final int PERCENT = 100;

    public boolean isCapitalArea(SigunguCode sigunguCode) {
        return sigunguCode != null && CAPITAL_AREA_SIDO_CODES.contains(sigunguCode.sidoCode());
    }

    /**
     * 한 기준월의 통계 행들을 비수도권 구인배수 분포로 접는다.
     *
     * <p>시군구 합계 분포와 직종 대분류별 분포를 함께 만든다 — 전체 구인배수는 수도권과
     * 비수도권이 사실상 같지만 직종별로는 2배 이상 갈리기 때문이다.
     */
    public NonCapitalRatioSnapshot snapshot(StatisticsMonth month, List<RegionJobStatistics> monthRows) {

        if (monthRows == null || monthRows.isEmpty()) {
            return NonCapitalRatioSnapshot.empty(month);
        }

        Map<SigunguCode, long[]> totals = new LinkedHashMap<>();
        Map<JobCode, Map<SigunguCode, long[]>> byJob = new LinkedHashMap<>();

        for (RegionJobStatistics row : monthRows) {
            if (isCapitalArea(row.sigunguCode())) {
                continue;
            }
            accumulate(totals, row.sigunguCode(), row);
            accumulate(byJob.computeIfAbsent(row.jobCode(), code -> new LinkedHashMap<>()),
                    row.sigunguCode(), row);
        }

        Map<JobCode, Map<SigunguCode, Double>> ratiosByJob = new LinkedHashMap<>();
        byJob.forEach((jobCode, sums) -> ratiosByJob.put(jobCode, toRatios(sums)));

        return NonCapitalRatioSnapshot.of(month, toRatios(totals), ratiosByJob);
    }

    /**
     * 분포 안에서 한 시군구의 순위를 낸다.
     *
     * <p><b>수도권 지역이면 언제나 비어 있다.</b> 이 지표의 정의가 "비수도권 시군구 안에서의
     * 위치" 이므로 모집단 밖의 지역에 값을 매기면 뜻이 없는 숫자가 된다. 전국 기준으로 슬쩍
     * 바꿔 채우면 같은 필드가 지역에 따라 다른 모집단을 가리키게 되어 더 나쁘다.
     * 구인배수를 계산할 수 없는 지역(유효구직자수 0)도 같은 이유로 비어 있다.
     */
    public Optional<NonCapitalRank> rankOf(SigunguCode sigunguCode, Map<SigunguCode, Double> distribution) {

        if (sigunguCode == null || distribution == null || distribution.isEmpty()) {
            return Optional.empty();
        }
        Double target = distribution.get(sigunguCode);
        if (target == null) {
            return Optional.empty();
        }

        int total = distribution.size();
        int below = 0;
        int equal = 0;
        int above = 0;
        for (Double value : distribution.values()) {
            int compared = Double.compare(value, target);
            if (compared < 0) {
                below++;
            } else if (compared > 0) {
                above++;
            } else {
                equal++;
            }
        }

        // 동점 처리가 들어간 표준 백분위(percentile rank). 동점 무리의 한가운데를 가리킨다.
        int percentile = clamp((int) Math.round(((below + (equal / 2.0)) / total) * PERCENT), 0, PERCENT);
        int rank = above + 1;
        int topPercent = clamp((int) Math.round(((double) rank / total) * PERCENT), 1, PERCENT);

        return Optional.of(new NonCapitalRank(percentile, topPercent, rank, total));
    }

    /** 분포 전체를 백분위 맵으로 바꾼다. 점수 정규화가 쓰는 경로다. */
    public Map<SigunguCode, Integer> percentiles(Map<SigunguCode, Double> distribution) {
        Map<SigunguCode, Integer> percentiles = new LinkedHashMap<>();
        if (distribution == null) {
            return percentiles;
        }
        for (SigunguCode code : distribution.keySet()) {
            rankOf(code, distribution).ifPresent(rank -> percentiles.put(code, rank.percentile()));
        }
        return percentiles;
    }

    private void accumulate(Map<SigunguCode, long[]> sums, SigunguCode code, RegionJobStatistics row) {
        long[] sum = sums.computeIfAbsent(code, key -> new long[2]);
        sum[0] += row.validOpenings();
        sum[1] += row.validSeekers();
    }

    /** 유효구직자수가 0 인 지역은 배수가 성립하지 않으므로 분포에서 뺀다. */
    private Map<SigunguCode, Double> toRatios(Map<SigunguCode, long[]> sums) {
        Map<SigunguCode, Double> ratios = new LinkedHashMap<>();
        sums.forEach((code, sum) -> {
            if (sum[1] > 0L) {
                ratios.put(code, (double) sum[0] / (double) sum[1]);
            }
        });
        return ratios;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
