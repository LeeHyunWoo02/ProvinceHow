package SDD.smash.domain.dwelling.infrastructure.batch;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.RentCollection;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.domain.dwelling.domain.port.RentRecordProvider;
import SDD.smash.domain.dwelling.domain.service.RentStatCalculator;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.DwellingByTypeUpsertRow;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.DwellingUpsertBundle;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.DwellingUpsertRow;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.WorkItem;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemProcessor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 시군구 하나에 대해 주택유형 3종을 수집해 통합 시세 1행 + 유형별 행들을 만든다.
 *
 * <p>Step 을 유형별로 쪼개지 않은 이유: 통합 <b>중앙값</b>은 유형별 중앙값들로 재구성할 수 없다.
 * 올바른 통합값을 내려면 3종의 원시 레코드를 한 곳에 풀링한 뒤 계산해야 한다.
 *
 * <p><b>부분 실패 정책</b> — 유형별 행은 (시군구, 유형) 단위로 격리한다. 성공한 유형은 그대로 적재하고
 * 실패한 유형만 건너뛴다. 반면 <b>통합값은 3종이 모두 성공했을 때만</b> 쓴다. 한 유형이라도 실패했는데
 * 남은 유형만 풀링해 덮어쓰면 이전 실행의 3종 통합값이 1~2종 값으로 조용히 열화되고,
 * 그 값은 {@code DwellingScorePolicy} 의 입력이라 추천 점수가 실제로 바뀌면서도 어떤 검증에도 걸리지 않는다.
 * 그래서 통합값은 {@code null} 로 두어 기존 {@code dwelling} 행을 보존한다.
 *
 * <p>자료가 없는 지역(전남·광주 등)은 이 정책으로 손해를 보지 않는다. 그런 응답은
 * {@code totalCount=0} + {@code resultCode=000} 이라 {@code CONFIRMED_EMPTY} 이고 실패가 아니다.
 * 완화가 실제로 구제하는 것은 일시적 네트워크 실패뿐이며, 그 이득은 점수 입력값 열화라는 대가보다 작다.
 * 한 유형 <b>안에서의</b> 부분 실패도 여전히 금지다 — 표본이 온전하지 않은 평균은
 * "값이 있는데 틀린 값"이 되기 때문이다({@link RentCollection} 참고).
 */
@Slf4j
@RequiredArgsConstructor
public class DwellingUpsertProcessor
        implements ItemProcessor<WorkItem, DwellingUpsertBundle>, StepExecutionListener {

    private final RentRecordProvider rentRecordProvider;

    /** 운영 확인용 유형별 누적치. Step 시작 때 초기화한다(같은 JVM 에서 Step 이 여러 번 돌 수 있다). */
    private final Map<HousingType, long[]> stats = new EnumMap<>(HousingType.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stats.clear();
    }

    @Override
    public DwellingUpsertBundle process(WorkItem work) {
        SigunguCode sigunguCode = work.sigunguCode();
        AggregationPeriod period = new AggregationPeriod(work.from(), work.to());

        List<Integer> pooledMonthly = new ArrayList<>();
        List<Integer> pooledJeonse = new ArrayList<>();
        List<DwellingByTypeUpsertRow> byType = new ArrayList<>();
        List<HousingType> failedTypes = new ArrayList<>();

        for (HousingType housingType : HousingType.values()) {
            RentCollection collection = rentRecordProvider.collect(housingType, sigunguCode, period);
            count(housingType, collection);

            if (collection.hasFailures()) {
                log.error("[dwelling] 유형 제외 - 수집 실패한 달이 있다 sigungu={}, type={}, failedMonths={}",
                        sigunguCode.value(), housingType, collection.failedMonths());
                failedTypes.add(housingType);
                continue;
            }

            List<Integer> monthly = valuesOf(collection.records(), RentRecord::isMonthly, RentRecord::monthlyRent);
            List<Integer> jeonse = valuesOf(collection.records(), RentRecord::isJeonse, RentRecord::deposit);

            pooledMonthly.addAll(monthly);
            pooledJeonse.addAll(jeonse);

            if (monthly.isEmpty() && jeonse.isEmpty()) {
                continue;
            }
            byType.add(DwellingByTypeUpsertRow.builder()
                    .sigunguCode(sigunguCode.value())
                    .housingType(housingType.name())
                    .monthAvg(RentStatCalculator.mean(monthly))
                    .monthMid(RentStatCalculator.median(monthly))
                    .jeonseAvg(RentStatCalculator.mean(jeonse))
                    .jeonseMid(RentStatCalculator.median(jeonse))
                    .build());
        }

        // 통합값은 3종이 모두 성공했을 때만 쓴다. 실패가 있으면 null 로 두어 기존 dwelling 행을 보존한다.
        boolean hasCombinedValues = !pooledMonthly.isEmpty() || !pooledJeonse.isEmpty();
        if (!failedTypes.isEmpty()) {
            log.warn("[dwelling] 통합값 건너뜀 - 실패한 유형이 있어 기존 dwelling 행을 보존한다 "
                            + "sigungu={}, failedTypes={}, keptByTypeRows={}",
                    sigunguCode.value(), failedTypes, byType.size());
        }

        DwellingUpsertRow combined = (failedTypes.isEmpty() && hasCombinedValues)
                ? DwellingUpsertRow.builder()
                .sigunguCode(sigunguCode.value())
                .monthAvg(RentStatCalculator.mean(pooledMonthly))
                .monthMid(RentStatCalculator.median(pooledMonthly))
                .jeonseAvg(RentStatCalculator.mean(pooledJeonse))
                .jeonseMid(RentStatCalculator.median(pooledJeonse))
                .build()
                : null;

        if (combined == null && byType.isEmpty()) {
            log.warn("[dwelling] 건너뜀 - 적재할 통합값도 유형별 행도 없다 sigungu={}", sigunguCode.value());
            return null;
        }
        return new DwellingUpsertBundle(combined, byType);
    }

    /** 호출량이 3배로 늘었으므로(약 9,500회/일) 유형별 호출 수를 완료 로그로 남긴다. */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        for (HousingType housingType : HousingType.values()) {
            long[] stat = stats.getOrDefault(housingType, new long[3]);
            log.info("[dwelling] 수집 요약 type={} apiCalls={} records={} failedTypeUnits={}",
                    housingType, stat[0], stat[1], stat[2]);
        }
        return null; // ExitStatus 를 바꾸지 않는다
    }

    private void count(HousingType housingType, RentCollection collection) {
        long[] stat = stats.computeIfAbsent(housingType, key -> new long[3]);
        stat[0] += collection.apiCalls();
        if (collection.hasFailures()) {
            stat[2]++;      // 버린 (시군구, 유형) 단위 수. 레코드는 집계에 쓰이지 않으므로 세지 않는다
        } else {
            stat[1] += collection.recordCount();
        }
    }

    private List<Integer> valuesOf(List<RentRecord> records,
                                   Predicate<RentRecord> filter,
                                   ToIntFunction<RentRecord> value) {
        return records.stream().filter(filter).map(value::applyAsInt).toList();
    }
}
