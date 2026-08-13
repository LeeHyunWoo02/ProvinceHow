package SDD.smash.global.batch.seed;

import SDD.smash.global.batch.launch.BatchGuard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;

import java.util.List;

/**
 * seedMasterJob 에서 Step 하나 앞에 서는 관문.
 *
 * <p>{@link FlowExecutionStatus} 로 {@code RUN} / {@code SKIP} 을 돌려주고,
 * 건너뛸 때는 <b>사유를 JobExecution 의 ExecutionContext 에 남긴다</b>.
 * 배치가 "성공했는데 데이터가 없다"를 사후에 설명할 수 있어야 하기 때문이다.
 *
 * <p>판정 순서(먼저 걸리는 사유가 기록된다)
 * <ol>
 *   <li>{@code seed.jobs.<키>.enabled=false} — 운영자가 껐다</li>
 *   <li>필수 설정 누락 — 파일 경로나 API 키가 비었다. <b>빈 키로 외부 API 를 호출하지 않는다</b></li>
 *   <li>기준일/기준월 파라미터 누락</li>
 *   <li>선행 기준 데이터 없음 — 부모 테이블이 비어 있다</li>
 *   <li>이미 같은 기준(seedVersion / baseDate / baseMonth)으로 완료됨 — 멱등 보장</li>
 * </ol>
 */
@Slf4j
public class SeedStepGate implements JobExecutionDecider {

    public static final FlowExecutionStatus RUN = new FlowExecutionStatus("RUN");
    public static final FlowExecutionStatus SKIP = new FlowExecutionStatus("SKIP");

    /** 건너뛴 사유를 담는 ExecutionContext 키 접두어. */
    public static final String SKIP_REASON_PREFIX = "seed.skipped.";

    private final SeedStepSpec spec;
    private final BatchGuard batchGuard;
    private final SeedDataPrerequisiteInspector prerequisiteInspector;

    public SeedStepGate(SeedStepSpec spec, BatchGuard batchGuard,
                        SeedDataPrerequisiteInspector prerequisiteInspector) {
        this.spec = spec;
        this.batchGuard = batchGuard;
        this.prerequisiteInspector = prerequisiteInspector;
    }

    public SeedStepSpec spec() {
        return spec;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {

        if (!spec.enabled()) {
            return skip(jobExecution, "비활성화됨(seed.jobs.*.enabled=false)");
        }

        List<String> missingConfigs = spec.missingConfigKeys();
        if (!missingConfigs.isEmpty()) {
            return skip(jobExecution, "필수 설정 누락 keys=" + missingConfigs);
        }

        String guardValue = jobExecution.getJobParameters().getString(spec.guardParameter());
        if (guardValue == null || guardValue.isBlank()) {
            return skip(jobExecution, "기준 파라미터 누락 parameter=" + spec.guardParameter());
        }

        for (String table : spec.requiredTables()) {
            if (!prerequisiteInspector.hasRows(table)) {
                return skip(jobExecution, "선행 기준 데이터 없음 table=" + table);
            }
        }

        if (batchGuard.stepAlreadyCompleted(spec.stepName(), spec.guardParameter(), guardValue)
                && dataStillPresent()) {
            return skip(jobExecution, "이미 완료됨 %s=%s".formatted(spec.guardParameter(), guardValue));
        }

        log.info("[batch] step={} group={} 실행 대상 {}={}",
                spec.stepName(), spec.group(), spec.guardParameter(), guardValue);
        return RUN;
    }

    /**
     * "이미 완료됨" 을 인정할지. 적재 대상 테이블이 비어 있으면 이력이 있어도 다시 돌린다
     * (배치 메타는 남고 data DB 만 지워진 상태를 자동 복구한다).
     */
    private boolean dataStillPresent() {
        return spec.selfTable() == null || prerequisiteInspector.hasRows(spec.selfTable());
    }

    private FlowExecutionStatus skip(JobExecution jobExecution, String reason) {
        jobExecution.getExecutionContext().putString(SKIP_REASON_PREFIX + spec.stepName(), reason);

        if (spec.group().isEssential()) {
            log.info("[batch] step={} group=ESSENTIAL 건너뜀 reason={}", spec.stepName(), reason);
        } else {
            log.warn("[batch] step={} group=EXTERNAL 건너뜀 - 미적재 데이터 reason={}", spec.stepName(), reason);
        }
        return SKIP;
    }
}
