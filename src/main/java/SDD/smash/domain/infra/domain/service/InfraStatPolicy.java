package SDD.smash.domain.infra.domain.service;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraRatio;
import SDD.smash.domain.infra.domain.model.InfraScore;
import SDD.smash.domain.infra.domain.model.RatioBasis;
import SDD.smash.domain.infra.domain.model.RegionIndustryCount;
import SDD.smash.domain.infra.domain.model.RegionIndustryStat;
import SDD.smash.global.domain.model.SigunguCode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시군구 × 업종 개수 스냅샷에서 {@code ratio} 와 {@code score} 를 계산한다.
 *
 * <h2>ratio — 시군구 내 업종 구성비</h2>
 * <pre>
 *   fraction(g, i) = count(g, i) / Σ_j count(g, j)      // 같은 시군구의 모든 업종 합
 *   ratio(g, i)    = RatioBasis.apply(fraction)          // 기본 PERCENT → × 100
 * </pre>
 * 시군구 전체 합이 0이면 {@code 0} 이다. 단위 해석은 {@link RatioBasis} 주석 참고 —
 * 코드로 판정 불가라 전환 가능하게 두었고 기본값만 정했다.
 *
 * <h2>score — 업종별 전국 백분위 (기본)</h2>
 * <pre>
 *   같은 업종 i 를 가진 시군구 집합 G_i (N = |G_i|)
 *   below = |{ h ∈ G_i : count(h,i) &lt; count(g,i) }|
 *   ties  = |{ h ∈ G_i : count(h,i) = count(g,i), h ≠ g }|
 *   score(g, i) = 100 × (below + 0.5 × ties) / (N - 1)      (N ≥ 2)
 *   score(g, i) = 50                                         (N = 1)
 * </pre>
 *
 * <h3>왜 백분위인가</h3>
 * <ul>
 *   <li><b>구조적으로 {@code [0, 100]} 이다.</b> {@code below + 0.5 × ties ≤ N - 1} 이므로
 *       상한 100 을 넘을 수 없다. {@code infra.score} 가 반드시 [0,100] 이어야 한다는 제약
 *       (추천 경로의 {@code Score.of()})을 계산식 자체가 보장한다. 정규화 상수를 손으로
 *       고르는 방식(예: {@code count / 최대치 × 100})은 최대치 추정이 틀리면 조용히 100을 넘는다.</li>
 *   <li><b>지역 규모 보정이 자연히 들어간다.</b> 절대 개수는 대도시가 항상 압도하지만,
 *       백분위는 "같은 업종에서 다른 시군구 대비 몇 번째인가"라 규모가 큰 지역에 점수가
 *       쏠리지 않고 분포 전체에 고르게 퍼진다.</li>
 *   <li><b>인구 대비(1인당 시설 수) 방식보다 의존이 적다.</b> 인구 정규화는 {@code population}
 *       테이블이 같은 기준일로 채워져 있어야 하고, 인구 결측 지역에서 0으로 나누기·발산이 생긴다.
 *       (인구 대비 값을 쓰더라도 결국 [0,100] 으로 정규화해야 하는데 그 정규화가 다시 백분위다.)</li>
 *   <li>동점을 midrank(0.5×ties)로 처리해 전 지역이 같은 값일 때 0점이 아니라 50점이 된다.
 *       "모두 같으면 아무도 특별하지 않다"가 0점보다 타당하다.</li>
 * </ul>
 *
 * <h3>주의 — 표본은 "수집된 시군구"다</h3>
 * 백분위의 모집단은 <b>이번 스냅샷에 그 업종 행이 있는 시군구</b>다. 수집 범위가 좁으면
 * (예: 호출 상한 때문에 일부 지역만 수집) 백분위의 의미가 달라진다. 그래서 부분 수집 상태의
 * 스냅샷을 반영하지 않는 것이 배치의 규칙이다.
 *
 * <p>반올림은 전 구간 {@code setScale(2, RoundingMode.HALF_UP)} 이다(기존 배치 규칙과 동일).
 * 나눗셈 중간 단계는 정밀도 손실을 막기 위해 {@link MathContext#DECIMAL64} 로 계산한 뒤
 * 마지막에 한 번만 자른다.
 *
 * <p>저장소·시간·랜덤에 의존하지 않는 순수 함수다.
 */
public class InfraStatPolicy {

    private static final MathContext DIVISION = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HALF = BigDecimal.valueOf(0.5);

    /** 표본이 1개뿐이면 분포가 없다. 중앙에 해당하는 값을 준다. */
    private static final BigDecimal SINGLE_SAMPLE_SCORE = BigDecimal.valueOf(50);

    private final RatioBasis ratioBasis;

    public InfraStatPolicy() {
        this(RatioBasis.DEFAULT);
    }

    public InfraStatPolicy(RatioBasis ratioBasis) {
        this.ratioBasis = ratioBasis == null ? RatioBasis.DEFAULT : ratioBasis;
    }

    public RatioBasis ratioBasis() {
        return ratioBasis;
    }

    /**
     * 개수 스냅샷 전체를 통계 행으로 바꾼다.
     *
     * <p>스냅샷 <b>전체</b>가 필요하다 — ratio 는 시군구 합계를, score 는 업종별 전국 분포를
     * 알아야 계산되기 때문이다. 행 단위로 계산할 수 없다.
     *
     * @param counts 시군구 × 업종 개수. 같은 조합이 여러 번 나오면 합산한다
     * @return 입력 순서를 유지한 통계 행 목록
     */
    public List<RegionIndustryStat> stats(List<RegionIndustryCount> counts) {
        if (counts == null || counts.isEmpty()) {
            return List.of();
        }

        Map<Key, Integer> merged = merge(counts);
        Map<SigunguCode, Long> regionTotals = regionTotals(merged);
        Map<IndustryCode, List<Integer>> industryDistribution = industryDistribution(merged);

        List<RegionIndustryStat> result = new ArrayList<>(merged.size());
        merged.forEach((key, count) -> {
            InfraRatio ratio = ratioOf(count, regionTotals.getOrDefault(key.sigunguCode(), 0L));
            InfraScore score = scoreOf(count, industryDistribution.get(key.industryCode()));
            result.add(new RegionIndustryStat(key.sigunguCode(), key.industryCode(), count, ratio, score));
        });
        return result;
    }

    /** 시군구 내 구성비. 분모가 0이면 0이다. */
    InfraRatio ratioOf(int count, long regionTotal) {
        if (regionTotal <= 0) {
            return InfraRatio.zero();
        }
        BigDecimal fraction = BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(regionTotal), DIVISION);
        return ratioBasis.apply(fraction);
    }

    /**
     * 같은 업종의 전국 분포 안에서의 백분위.
     *
     * @param sortedCounts 오름차순 정렬된 같은 업종의 시군구별 개수 전체(자기 자신 포함)
     */
    InfraScore scoreOf(int count, List<Integer> sortedCounts) {
        if (sortedCounts == null || sortedCounts.isEmpty()) {
            return InfraScore.of(SINGLE_SAMPLE_SCORE);
        }
        int total = sortedCounts.size();
        if (total == 1) {
            return InfraScore.of(SINGLE_SAMPLE_SCORE);
        }

        int below = lowerBound(sortedCounts, count);
        int atOrBelow = upperBound(sortedCounts, count);
        int tiesIncludingSelf = atOrBelow - below;
        int tiesExcludingSelf = Math.max(0, tiesIncludingSelf - 1);

        BigDecimal numerator = BigDecimal.valueOf(below)
                .add(HALF.multiply(BigDecimal.valueOf(tiesExcludingSelf)));
        BigDecimal percentile = numerator
                .divide(BigDecimal.valueOf(total - 1L), DIVISION)
                .multiply(HUNDRED);

        // 부동소수 오차로 100.0000001 이 되는 일이 없도록 자른 뒤 한 번 더 조인다.
        BigDecimal bounded = percentile.setScale(InfraScore.SCALE, RoundingMode.HALF_UP);
        if (bounded.compareTo(InfraScore.MAX) > 0) {
            bounded = InfraScore.MAX;
        } else if (bounded.signum() < 0) {
            bounded = InfraScore.MIN;
        }
        return InfraScore.of(bounded);
    }

    // ------------------------------------------------------------------ 내부

    private Map<Key, Integer> merge(List<RegionIndustryCount> counts) {
        Map<Key, Integer> merged = new LinkedHashMap<>();
        for (RegionIndustryCount row : counts) {
            if (row == null) {
                continue;
            }
            merged.merge(new Key(row.sigunguCode(), row.industryCode()), row.count(), Integer::sum);
        }
        return merged;
    }

    private Map<SigunguCode, Long> regionTotals(Map<Key, Integer> merged) {
        Map<SigunguCode, Long> totals = new HashMap<>();
        merged.forEach((key, count) -> totals.merge(key.sigunguCode(), (long) count, Long::sum));
        return totals;
    }

    private Map<IndustryCode, List<Integer>> industryDistribution(Map<Key, Integer> merged) {
        Map<IndustryCode, List<Integer>> distribution = new HashMap<>();
        merged.forEach((key, count) ->
                distribution.computeIfAbsent(key.industryCode(), k -> new ArrayList<>()).add(count));
        distribution.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        return distribution;
    }

    /** 정렬된 목록에서 {@code value} 미만인 원소 수. */
    private static int lowerBound(List<Integer> sorted, int value) {
        int low = 0;
        int high = sorted.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sorted.get(mid) < value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** 정렬된 목록에서 {@code value} 이하인 원소 수. */
    private static int upperBound(List<Integer> sorted, int value) {
        int low = 0;
        int high = sorted.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sorted.get(mid) <= value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private record Key(SigunguCode sigunguCode, IndustryCode industryCode) {
    }
}
