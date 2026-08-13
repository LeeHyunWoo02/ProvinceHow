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

    private List<String> failedStepsOf(JobExecution jobExecution, SeedGroup group) {
        List<String> failed = new ArrayList<>();
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStatus() == BatchStatus.FAILED
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
