package SDD.smash.domain.dwelling.application;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.MonthlyRentResult;
import SDD.smash.domain.dwelling.domain.port.RentRecordProvider;
import SDD.smash.domain.dwelling.domain.service.DwellingBaseMonthPolicy;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * 전월세 배치가 어느 달을 기준으로 돌아야 하는지 결정하는 유스케이스.
 *
 * <p>규칙 자체는 {@link DwellingBaseMonthPolicy}(순수 도메인)가 갖는다.
 * 여기가 갖는 것은 <b>시계</b>와 <b>설정값</b>, 그리고 <b>공급 기관 탐침</b>이다 — 셋 다 도메인이 몰라야 하는 것들이다.
 *
 * <p>배치·스케줄러는 이 Service 를 통해서만 기준월을 얻는다(architecture-conventions §6.3).
 * {@code LocalDate.now()} 를 직접 부르는 코드가 생기면 그 순간 이 계산은 테스트할 수 없게 된다.
 *
 * <p>트랜잭션을 걸지 않는다. DB 를 건드리지 않고 외부 API 를 호출하므로
 * 트랜잭션으로 감싸면 커넥션을 쥔 채 네트워크를 기다리게 된다(persistence-conventions §6.3).
 */
@Service
@Slf4j
public class DwellingBaseMonthService {

    /** 국토부 실거래 공개 시점 기준 시간대. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final RentRecordProvider rentRecordProvider;
    private final DwellingBaseMonthPolicy policy = new DwellingBaseMonthPolicy();
    private final Clock clock;

    private final int confirmedLagMonths;
    private final int lookbackMonths;
    private final int maxFallbackMonths;
    private final String configuredOverride;
    private final boolean probeEnabled;
    private final String probeRegionCode;

    @Autowired
    public DwellingBaseMonthService(
            RentRecordProvider rentRecordProvider,
            @Value("${dwelling.confirmedLagMonths:2}") int confirmedLagMonths,
            @Value("${dwelling.months:10}") int lookbackMonths,
            @Value("${dwelling.maxFallbackMonths:3}") int maxFallbackMonths,
            @Value("${dwelling.dealYmd:}") String configuredOverride,
            @Value("${dwelling.baseMonthProbe.enabled:true}") boolean probeEnabled,
            @Value("${dwelling.baseMonthProbe.regionCode:11110}") String probeRegionCode) {
        this(rentRecordProvider, Clock.system(ZONE), confirmedLagMonths, lookbackMonths,
                maxFallbackMonths, configuredOverride, probeEnabled, probeRegionCode);
    }

    /** 테스트에서 고정 {@link Clock} 을 넣기 위한 생성자. */
    public DwellingBaseMonthService(RentRecordProvider rentRecordProvider,
                                    Clock clock,
                                    int confirmedLagMonths,
                                    int lookbackMonths,
                                    int maxFallbackMonths,
                                    String configuredOverride,
                                    boolean probeEnabled,
                                    String probeRegionCode) {
        this.rentRecordProvider = rentRecordProvider;
        this.clock = clock;
        this.confirmedLagMonths = confirmedLagMonths;
        this.lookbackMonths = lookbackMonths;
        this.maxFallbackMonths = maxFallbackMonths;
        this.configuredOverride = configuredOverride;
        this.probeEnabled = probeEnabled;
        this.probeRegionCode = probeRegionCode;
    }

    /** Asia/Seoul 기준 현재 연월. */
    public YearMonth currentMonth() {
        return YearMonth.from(LocalDate.now(clock.withZone(ZONE)));
    }

    /**
     * 이번 실행이 써야 할 기준월.
     *
     * <ol>
     *   <li>수동 override 가 있으면 그대로 쓴다. 운영자가 명시한 것을 자동 계산이 덮지 않는다</li>
     *   <li>없으면 {@code 현재월 - confirmedLagMonths} 에서 출발한다</li>
     *   <li>그 달이 <b>확정 0건</b>이면 직전 월로 물러난다(최대 {@code maxFallbackMonths} 회)</li>
     *   <li>탐침 자체가 실패하면(장애·인증오류) 물러나지 않고 규칙상 기준월을 그대로 쓴다.
     *       장애를 "자료 없음"으로 오해해 엉뚱하게 과거로 가면 안 되기 때문이다</li>
     * </ol>
     */
    public YearMonth resolveBaseMonth() {
        YearMonth override = policy.parseOverride(configuredOverride);
        if (override != null) {
            log.info("[DwellingBaseMonth] override 적용 baseMonth={}", policy.format(override));
            return override;
        }

        List<YearMonth> candidates =
                policy.baseMonthCandidates(currentMonth(), confirmedLagMonths, maxFallbackMonths);
        YearMonth primary = candidates.get(0);

        if (!probeEnabled) {
            log.info("[DwellingBaseMonth] 자동 계산 baseMonth={} (탐침 비활성)", policy.format(primary));
            return primary;
        }

        SigunguCode probeRegion = probeRegion();
        if (probeRegion == null) {
            return primary;
        }

        for (YearMonth candidate : candidates) {
            // 탐침은 아파트로 고정한다. 목적이 "국토부 신고 인프라에 이번 달 자료가 확정 반영됐는가"
            // 확인이고, 거래량이 가장 많은 아파트라야 확정 0건과 장애를 가장 명확히 구분할 수 있다.
            MonthlyRentResult probe =
                    rentRecordProvider.fetchMonth(HousingType.APARTMENT, probeRegion, candidate);
            switch (probe.status()) {
                case AVAILABLE -> {
                    log.info("[DwellingBaseMonth] 자동 계산 baseMonth={}, 탐침지역={}, 탐침건수={}",
                            policy.format(candidate), probeRegion.value(), probe.reportedTotal());
                    return candidate;
                }
                case CONFIRMED_EMPTY -> log.warn(
                        "[DwellingBaseMonth] 확정 0건이라 직전 월로 물러난다 candidate={}, 탐침지역={}",
                        policy.format(candidate), probeRegion.value());
                case UNDETERMINED -> {
                    log.error("[DwellingBaseMonth] 탐침 실패로 규칙상 기준월을 유지한다 baseMonth={}, 사유={}",
                            policy.format(primary), probe.failureReason());
                    return primary;
                }
            }
        }

        log.warn("[DwellingBaseMonth] 후보 {}개가 모두 0건이라 규칙상 기준월을 유지한다 baseMonth={}",
                candidates.size(), policy.format(primary));
        return primary;
    }

    /** 배치 JobParameter 로 넘길 {@code yyyyMM} 표기. */
    public String resolveBaseMonthText() {
        return policy.format(resolveBaseMonth());
    }

    /** 이번 실행이 모을 집계 구간. */
    public AggregationPeriod resolveAggregationPeriod() {
        return policy.aggregationPeriod(resolveBaseMonth(), lookbackMonths);
    }

    /** 집계 개월 수 설정값. 배치 JobParameter {@code months} 로 넘긴다. */
    public int lookbackMonths() {
        return lookbackMonths;
    }

    private SigunguCode probeRegion() {
        try {
            return SigunguCode.of(probeRegionCode);
        } catch (RuntimeException e) {
            log.warn("[DwellingBaseMonth] 탐침 지역 코드가 유효하지 않아 탐침을 건너뛴다 code={}", probeRegionCode);
            return null;
        }
    }
}
