package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.port.InfraFacilityProvider;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraCollectTarget;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraTargetResult;
import SDD.smash.domain.infra.infrastructure.external.LocalDataCallBudgetExceededException;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 아직 수집하지 않은 대상만 하나씩 읽어 외부 API 를 호출한다. {@code infraCollectStep} 의 Reader.
 *
 * <h2>예산 소진은 실패가 아니라 스트림의 끝이다</h2>
 * Spring Batch 는 Reader 가 {@code null} 을 돌려줘야 스트림이 끝나고 Step 이 COMPLETED 가 된다.
 * 그래서 호출 예산이 바닥나면 <b>예외를 올리지 않고 {@code null} 을 반환</b>한다. 지금까지 모은
 * 청크는 staging 에 커밋돼 있고, 남은 대상은 다음 실행이 이어받는다. 5시간 수집분을 통째로
 * 버리던 2026-08 운영 실측이 이 설계의 이유다.
 *
 * <p>대상 <b>처리 중간에</b> 예산이 끊기면 그 대상은 결과를 만들지 않는다 — 부분 페이지를
 * 저장하지 않으므로 미완료로 남고 다음 실행이 처음부터 다시 받는다.
 *
 * <h2>일시적 오류와는 구분한다</h2>
 * 타임아웃·5xx 는 어댑터가 지수 백오프로 재시도하고, 그래도 실패한 대상은 <b>실행 내 2차 패스</b>에
 * 한 번 더 올린다. 2차에서도 실패하면 그 대상만 미완료로 남는다 — 회차가 완성되지 않았을 뿐이라
 * 다음 실행이 이어받는다. 여기서 Step 을 실패시키지 않는 것이 이 구조의 핵심이다.
 *
 * <p><b>부분 반영은 여전히 금지다.</b> 이 Reader 는 staging 에만 쓴다. 서비스 테이블({@code infra})은
 * 회차가 완성됐을 때 {@code infraStep} 이 갱신한다.
 *
 * <h2>영영 완성되지 않는 회차를 드러낸다</h2>
 * 존재하지 않는 (기관, 업종) 조합이나 매핑 오타처럼 <b>영구 실패</b> 대상이 하나만 있어도 회차는
 * 완성되지 않고 {@code infra} 테이블은 갱신되지 않는데, Step 은 매일 COMPLETED 를 낸다.
 * 그래서 {@code run_key}(= 회차 시작일)로부터 {@code stallThresholdDays} 가 지나도록 남은 대상이
 * 있으면 {@code log.error} 로 알리고 2차 패스 실패 대상 표본을 함께 남긴다.
 * <b>영구 제외 같은 정책은 만들지 않는다</b> — 무엇을 포기할지는 사람이 정할 문제라 관측까지만 한다.
 */
@Slf4j
public class InfraTargetCollectReader implements ItemReader<InfraTargetResult> {

    /** stall 로그에 남길 실패 대상 표본 수. 전체를 남기면 로그가 수천 줄이 된다. */
    private static final int UNRESOLVED_SAMPLE_LIMIT = 10;

    private final String runKey;
    private final InfraFacilityProvider provider;
    private final Map<LocalDataRegionCode, SigunguCode> regionIndex;
    private final Map<SigunguCode, RegionCodeMapping.DistrictSplit> districtSplits;

    /** stall 판정 기준일. 회차 시작일과의 경과일을 여기서 잰다. */
    private final LocalDate today;
    private final int stallThresholdDays;
    private final List<String> unresolvedSamples = new ArrayList<>(UNRESOLVED_SAMPLE_LIMIT);

    private final Deque<InfraCollectTarget> queue = new ArrayDeque<>();
    private final Deque<InfraCollectTarget> retryQueue = new ArrayDeque<>();
    private final Set<String> unmappedRegions = new LinkedHashSet<>();
    private final Set<String> unresolvedCities = new LinkedHashSet<>();

    private final int plannedTargets;
    private boolean secondPass;
    private boolean finished;

    private int collectedTargets;
    private int emptyTargets;
    private int apiCalls;
    private int readCount;
    private int filteredOut;
    private int duplicates;
    private int unmappedFacilities;
    private int districtResolved;
    private int districtUnresolved;
    private int firstPassFailures;
    private int unresolvedTargets;
    private boolean budgetExhausted;
    private String lastFailureReason;

    /**
     * @param today              stall 판정 기준일(회차 시작일과의 경과일 계산용)
     * @param stallThresholdDays 이 일수를 넘도록 회차가 안 채워지면 {@code log.error} 로 알린다
     */
    public InfraTargetCollectReader(String runKey,
                                    InfraFacilityProvider provider,
                                    List<InfraCollectTarget> pendingTargets,
                                    Map<LocalDataRegionCode, SigunguCode> regionIndex,
                                    Map<SigunguCode, RegionCodeMapping.DistrictSplit> districtSplits,
                                    LocalDate today,
                                    int stallThresholdDays) {
        this.runKey = runKey;
        this.provider = provider;
        this.today = today;
        this.stallThresholdDays = stallThresholdDays;
        this.regionIndex = regionIndex == null ? Map.of() : regionIndex;
        this.districtSplits = districtSplits == null ? Map.of() : districtSplits;
        this.queue.addAll(pendingTargets == null ? List.of() : pendingTargets);
        this.plannedTargets = this.queue.size();
    }

    @Override
    public InfraTargetResult read() {
        while (!finished) {
            if (!provider.hasRemainingCapacity()) {
                budgetExhausted = true;
                return end();
            }
            InfraCollectTarget target = next();
            if (target == null) {
                return end();
            }

            FacilityCollection collection;
            try {
                collection = provider.collect(target.industryCode(), target.regionCode());
            } catch (LocalDataCallBudgetExceededException e) {
                // 대상 처리 중간에 예산이 끊겼다. 이 대상은 기록하지 않아 미완료로 남는다.
                budgetExhausted = true;
                lastFailureReason = e.getMessage();
                return end();
            } catch (RuntimeException e) {
                recordFailure(target, e);
                continue;
            }
            return toResult(target, collection);
        }
        return null;
    }

    // ------------------------------------------------------------------ 진행 상태

    /**
     * 이번 실행이 이어받은 수집 회차. Writer 가 <b>같은 키로</b> staging 에 쓴다 —
     * 계획과 저장이 다른 회차를 가리키면 이어달리기가 깨진다.
     */
    public String runKey() {
        return runKey;
    }

    /** 이번 실행에서 수집을 마친 대상 수. */
    public int collectedTargets() {
        return collectedTargets;
    }

    /** 예산 소진으로 멈췄는가. 실패가 아니라 "오늘은 여기까지"다. */
    public boolean budgetExhausted() {
        return budgetExhausted;
    }

    /** 2차 패스에서도 수집하지 못해 미완료로 남긴 대상 수. */
    public int unresolvedTargets() {
        return unresolvedTargets;
    }

    /**
     * 회차가 임계 일수를 넘도록 완성되지 않았는가. {@code true} 면 이어달리기가 진행되지 않고
     * 있다는 뜻이고 {@code end()} 가 {@code log.error} 로 알린다.
     *
     * <p>회차 키가 날짜 형식이 아니면 나이를 잴 수 없어 항상 {@code false} 다.
     */
    public boolean runStalled() {
        if (plannedTargets - collectedTargets <= 0 || today == null || stallThresholdDays <= 0) {
            return false;
        }
        return runAgeDays() >= stallThresholdDays;
    }

    /** stall 로그에 남기는 2차 패스 실패 표본(최대 {@value #UNRESOLVED_SAMPLE_LIMIT}건). */
    public List<String> unresolvedSamples() {
        return List.copyOf(unresolvedSamples);
    }

    /** Step 로그 한 줄. 지표를 한 곳에 모은다. */
    public String summary() {
        return String.format(
                "planned=%d, collected=%d, empty=%d, unresolved=%d, retried=%d, budgetExhausted=%s, "
                        + "apiCalls=%d, read=%d, filteredOut=%d, duplicates=%d, "
                        + "unmappedFacilities=%d, unmappedRegions=%d, districtResolved=%d, districtUnresolved=%d",
                plannedTargets, collectedTargets, emptyTargets, unresolvedTargets, firstPassFailures,
                budgetExhausted, apiCalls, readCount, filteredOut, duplicates,
                unmappedFacilities, unmappedRegions.size(), districtResolved, districtUnresolved);
    }

    // ------------------------------------------------------------------ 내부

    private InfraTargetResult end() {
        finished = true;
        if (budgetExhausted) {
            log.info("[infraJob] 호출 예산 소진으로 수집을 멈춘다(실패 아님). collected={}/{}, reason={}",
                    collectedTargets, plannedTargets,
                    lastFailureReason == null ? "예산 잔량 없음" : lastFailureReason);
        }
        if (unresolvedTargets > 0) {
            log.warn("[infraJob] 2차 패스에서도 수집하지 못한 대상 {}건은 미완료로 남긴다. 마지막 사유={}, 표본={}",
                    unresolvedTargets, lastFailureReason, unresolvedSamples);
        }
        warnIfRunStalled();
        if (districtUnresolved > 0) {
            log.warn("[infraJob] 주소에서 일반구를 찾지 못해 {}건을 제외했다. resolved={}, cities={}",
                    districtUnresolved, districtResolved, unresolvedCities);
        }
        if (!unmappedRegions.isEmpty()) {
            log.warn("[infraJob] 시군구 매핑에 없는 개방자치단체코드 {}종({}건)을 제외했다. codes={}",
                    unmappedRegions.size(), unmappedFacilities, unmappedRegions);
        }
        return null;
    }

    /**
     * 회차가 임계 일수를 넘도록 완성되지 않으면 알린다.
     *
     * <p>{@code run_key} 는 회차 시작일이므로 오늘과의 차이가 곧 이 회차의 나이다. 예산 때문에
     * 며칠 걸리는 것은 정상이지만, 기대 소요일에 여유를 더한 임계치를 넘도록 남은 대상이 있으면
     * 이어달리기가 <b>진행되지 않고 있다</b>는 뜻이다(영구 실패 대상, 매핑 오타 등).
     *
     * <p>회차 키가 날짜 형식이 아니면(테스트 등) 판정하지 않는다.
     */
    private void warnIfRunStalled() {
        if (!runStalled()) {
            return;
        }
        int remaining = plannedTargets - collectedTargets;
        long ageDays = runAgeDays();
        log.error("[infraJob] 회차가 {}일째 완성되지 않았다 runKey={}, 남은 대상={}/{}, 미해결={}, "
                        + "임계일수={}. infra 테이블은 그동안 갱신되지 않는다. "
                        + "2차 패스 실패 표본(최대 {}건)={}, 마지막 사유={}",
                ageDays, runKey, remaining, plannedTargets, unresolvedTargets, stallThresholdDays,
                UNRESOLVED_SAMPLE_LIMIT, unresolvedSamples, lastFailureReason);
    }

    /** 회차 시작일로부터 오늘까지의 일수. 회차 키가 날짜가 아니면 {@code -1}. */
    private long runAgeDays() {
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(runKey), today);
        } catch (DateTimeParseException | NullPointerException e) {
            return -1L;
        }
    }

    /** 1차 큐가 비면 실패 대상만 모아 2차 패스로 넘어간다. */
    private InfraCollectTarget next() {
        InfraCollectTarget target = queue.poll();
        if (target != null) {
            return target;
        }
        if (!secondPass) {
            secondPass = true;
            if (plannedTargets > 0) {
                // 대상이 0건이면(체크포인트를 쓰지 않는 CSV 경로 등) 수집 로그 자체가 오해를 부른다.
                log.info("[infraJob] 1차 수집 완료 성공={}건 / 실패={}건", collectedTargets, firstPassFailures);
            }
            if (!retryQueue.isEmpty()) {
                log.info("[infraJob] 1차 실패 {}건 → 2차 패스 시작", retryQueue.size());
                queue.addAll(retryQueue);
                retryQueue.clear();
            }
            return queue.poll();
        }
        return null;
    }

    private void recordFailure(InfraCollectTarget target, RuntimeException e) {
        lastFailureReason = e.getMessage();
        if (secondPass) {
            unresolvedTargets++;
            if (unresolvedSamples.size() < UNRESOLVED_SAMPLE_LIMIT) {
                unresolvedSamples.add(target.regionCodeValue() + "/" + target.industryCodeValue());
            }
            log.debug("[infraJob] 2차 패스에서도 실패 industry={}, org={}, reason={}",
                    target.industryCodeValue(), target.regionCodeValue(), e.getMessage());
            return;
        }
        firstPassFailures++;
        retryQueue.add(target);
        log.debug("[infraJob] 대상 수집 실패 industry={}, org={}, reason={}",
                target.industryCodeValue(), target.regionCodeValue(), e.getMessage());
    }

    private InfraTargetResult toResult(InfraCollectTarget target, FacilityCollection collection) {
        InfraFacilityTally.Result tally = InfraFacilityTally.tally(collection, regionIndex, districtSplits);

        collectedTargets++;
        apiCalls += collection.apiCalls();
        readCount += collection.readCount();
        filteredOut += collection.filteredOutCount();
        duplicates += collection.duplicatesDropped();
        unmappedFacilities += tally.unmappedFacilityCount();
        districtResolved += tally.districtResolved();
        districtUnresolved += tally.districtUnresolved();
        unmappedRegions.addAll(tally.unmappedRegions());
        unresolvedCities.addAll(tally.unresolvedCities());
        if (tally.countedFacilities() == 0) {
            emptyTargets++;
        }

        return new InfraTargetResult(target, tally.counts(), tally.countedFacilities(),
                collection.apiCalls(), collection.readCount(), collection.filteredOutCount(),
                collection.duplicatesDropped(), tally.unmappedFacilityCount(),
                tally.districtResolved(), tally.districtUnresolved());
    }
}
