package SDD.smash.global.batch.listener;

import SDD.smash.global.batch.seed.SeedGroup;
import SDD.smash.global.batch.seed.SeedStepGate;
import SDD.smash.global.batch.seed.SeedStepSpec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * seedMasterJob 이 끝난 뒤 <b>필수 기준 데이터 실패</b>와 <b>외부 갱신 데이터 실패</b>를 갈라 기록한다.
 *
 * <p>Spring Batch 는 Step 이 실패하면 Job 도 FAILED 로 끝낼 뿐 "무엇이 왜 비었는지"는 남기지 않는다.
 * 이 리스너가 그 공백을 메운다.
 * <ul>
 *   <li>필수 실패 → Job 은 이미 FAILED 다. 사유를 exitDescription 에 적고 ERROR 로그를 남긴다</li>
 *   <li>외부만 실패 → Job 은 COMPLETED 지만 exitCode 를
 *       {@link #COMPLETED_WITH_EXTERNAL_FAILURES} 로 낮춰 구분할 수 있게 한다</li>
 *   <li>건너뛴 Step → {@link SeedStepGate} 가 남긴 사유를 모아 "미적재 데이터" 로 경고한다</li>
 * </ul>
 *
 * <p>{@code afterJob} 은 배치 메타가 최종 갱신되기 <b>직전</b>에 호출되므로 여기서 바꾼 exitStatus 가
 * 그대로 저장된다. ExecutionContext 는 마지막 Step 이후에 자동 저장되지 않을 수 있어 직접 갱신한다.
 */
@Slf4j
public class SeedMasterJobListener implements JobExecutionListener {

    public static final String COMPLETED_WITH_EXTERNAL_FAILURES = "COMPLETED_WITH_EXTERNAL_FAILURES";

    private final Map<String, SeedGroup> groupByStepName;
    private final JobRepository jobRepository;

    public SeedMasterJobListener(List<SeedStepSpec> specs, JobRepository jobRepository) {
        Map<String, SeedGroup> groups = new LinkedHashMap<>();
        specs.forEach(spec -> groups.put(spec.stepName(), spec.group()));
        this.groupByStepName = groups;
        this.jobRepository = jobRepository;
    }

    /** 필수 기준 데이터 Step 중 실패한 것들. 비어 있지 않으면 애플리케이션을 준비 완료로 보면 안 된다. */
    public List<String> failedEssentialSteps(JobExecution jobExecution) {
        return failedStepsOf(jobExecution, SeedGroup.ESSENTIAL);
    }

    public List<String> failedExternalSteps(JobExecution jobExecution) {
        return failedStepsOf(jobExecution, SeedGroup.EXTERNAL);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        List<String> failedEssential = failedEssentialSteps(jobExecution);
        List<String> failedExternal = failedExternalSteps(jobExecution);
        Map<String, String> skipped = skipReasons(jobExecution);

        skipped.forEach((stepName, reason) -> {
            if (groupByStepName.get(stepName) == SeedGroup.EXTERNAL) {
                log.warn("[seed] 미적재 데이터 step={} reason={}", stepName, reason);
            }
        });

        if (!failedEssential.isEmpty() || jobExecution.getStatus() == BatchStatus.FAILED) {
            String reason = failedEssential.isEmpty()
                    ? "seedMasterJob 실패"
                    : "필수 기준 데이터 적재 실패 steps=" + failedEssential;
            jobExecution.setExitStatus(jobExecution.getExitStatus().addExitDescription(reason));
            log.error("[seed] {} - 애플리케이션을 준비 완료로 처리하지 않는다. failedExternal={}, skipped={}",
                    reason, failedExternal, skipped.keySet());

        } else if (!failedExternal.isEmpty()) {
            jobExecution.setExitStatus(new ExitStatus(COMPLETED_WITH_EXTERNAL_FAILURES,
                    "외부 갱신 데이터 적재 실패 steps=" + failedExternal));
            log.error("[seed] 외부 갱신 데이터 적재 실패 steps={} - 기준 데이터는 정상이므로 서비스는 계속한다.",
                    failedExternal);

        } else {
            log.info("[seed] seedMasterJob 완료 실행={} 건너뜀={}",
                    executedStepNames(jobExecution), skipped.keySet());
        }

        try {
            jobRepository.updateExecutionContext(jobExecution);
        } catch (RuntimeException e) {
            log.warn("[seed] JobExecutionContext 저장 실패 - 건너뛴 사유가 메타에 남지 않는다. reason={}",
                    e.getMessage());
        }
    }

    /** {@link SeedStepGate} 가 남긴 건너뜀 사유. key = stepName */
    public Map<String, String> skipReasons(JobExecution jobExecution) {
        ExecutionContext context = jobExecution.getExecutionContext();
        Map<String, String> reasons = new LinkedHashMap<>();
        for (String stepName : groupByStepName.keySet()) {
            String key = SeedStepGate.SKIP_REASON_PREFIX + stepName;
            if (context.containsKey(key)) {
                reasons.put(stepName, context.getString(key));
            }
        }
        return reasons;
    }

    /**
     * 해당 그룹에서 <b>정상 종료하지 못한</b> Step 이름들.
     *
     * <h3>왜 {@code BatchStatus.FAILED} 만 보면 안 되는가</h3>
     * Spring Batch 는 <b>실패한 Step 을 지나쳐 흐름이 계속되면 그 StepExecution 의 상태를
     * {@code FAILED} 에서 {@code ABANDONED} 로 올린다.</b> 재시작할 때 그 지점에서 이어받지 말라는
     * 표시다(ExitStatus 는 {@code FAILED} 로 남는다).
     * {@code SeedMasterJobConfig#seedMasterFlow} 에서 EXTERNAL Step 은
     * {@code from(step).on("*").to(다음 관문)} 이라 실패해도 흐름이 이어지므로,
     * <b>흐름상 마지막이 아닌 EXTERNAL Step 이 실패하면 항상 {@code ABANDONED} 가 된다.</b>
     * 그래서 {@code FAILED} 만 세면 중간 Step 실패가 통째로 누락되고, Job 이 "완료" 로 보고된다
     * (운영 실측: infraStep 이 ABANDONED 라 인프라 0건인데 exitStatus 가 COMPLETED 로 남았다).
     *
     * <h3>왜 "COMPLETED 가 아니면 실패" 인가</h3>
     * <ul>
     *   <li><b>건너뛴 Step 은 StepExecution 자체가 만들어지지 않는다.</b> {@link SeedStepGate} 는
     *       {@code JobExecutionDecider} 라 SKIP 이면 Step 이 실행되지 않아
     *       {@code BATCH_STEP_EXECUTION} 에 행이 남지 않는다. 즉 여기 순회 대상은
     *       <b>실제로 실행된 Step 뿐</b>이라 "실행됐는데 COMPLETED 가 아니다 = 실패" 가 성립한다.</li>
     *   <li>{@code FAILED || ABANDONED} 로 나열하는 방식보다 {@code STOPPED}/{@code UNKNOWN} 같은
     *       비정상 종료 상태까지 자연히 덮는다. 상태가 늘어나도 이 메서드를 다시 고칠 필요가 없다.</li>
     * </ul>
     * ESSENTIAL Step 은 {@code on("FAILED").fail()} 로 Job 이 거기서 끝나므로 보통 {@code FAILED} 로
     * 남는다. 판정이 넓어져도 기존 결과는 그대로다.
     */
    private List<String> failedStepsOf(JobExecution jobExecution, SeedGroup group) {
        List<String> failed = new ArrayList<>();
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStatus() != BatchStatus.COMPLETED
                    && groupByStepName.get(stepExecution.getStepName()) == group) {
                failed.add(stepExecution.getStepName());
            }
        }
        return failed;
    }

    private Set<String> executedStepNames(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
