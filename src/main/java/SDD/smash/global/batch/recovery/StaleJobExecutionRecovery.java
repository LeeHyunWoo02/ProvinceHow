package SDD.smash.global.batch.recovery;

import SDD.smash.global.batch.launch.BatchGuard;
import SDD.smash.global.batch.launch.RunningJobExecution;
import SDD.smash.global.metrics.BatchExecutionMetrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 기동 시 <b>고아 실행</b>(직전 JVM 이 죽어 {@code END_TIME IS NULL} 로 남은 실행)을 FAILED 로 닫는다.
 *
 * <p>Spring Batch 의 실행 중 판정은 {@code JOB_NAME = ? AND END_TIME IS NULL} 이라 JobInstance 와
 * 무관하게 <b>jobName 단위로 영구 차단</b>된다. 재배포가 진행 중이던 배치를 SIGKILL 하면 그 이후의
 * 모든 기동이 "이전 실행이 아직 진행 중이다" 로 건너뛰기가 되고, 로그 한 줄 외에 증상이 없다.
 * 이 컴포넌트가 그 행을 닫는 유일한 경로다.
 *
 * <p><b>판정은 스텝 하트비트로 한다.</b> Job 행의 {@code LAST_UPDATED} 는 시작/종료 때만 갱신되지만
 * StepExecution 은 청크 커밋마다 저장된다. 그래서 "그 실행에 속한 StepExecution 의 최신
 * {@code LAST_UPDATED}" 가 임계({@code batch.recovery.stale.threshold}, 기본 15분)를 넘은 것만
 * 정리한다. 임계 미만은 살아 있는 실행일 수 있으므로 경고만 남기고 손대지 않는다.
 *
 * <p>{@code SeedMasterJobLauncher} 보다 <b>먼저</b> 돌아야 정리 결과가 그 기동에 반영된다
 * ({@link #ORDER}). 상태 변경은 {@code JobRepository.update} 로만 한다 — 직접 SQL UPDATE 는
 * {@code VERSION} 낙관락과 정합 검사를 우회한다.
 *
 * <p>이 프로젝트는 <b>단일 인스턴스</b>를 전제한다. 다만 나이 임계 + 스텝 하트비트 판정이 있어,
 * 설령 인스턴스가 둘이어도 진행 중인(하트비트가 도는) 실행은 정리 대상이 되지 않는다.
 */
@Component
@Slf4j
public class StaleJobExecutionRecovery {

    /** {@code SeedMasterJobLauncher} 보다 먼저 돈다. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    private static final String MARKER = "STALE_RECOVERED";

    private final BatchGuard batchGuard;
    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final BatchExecutionMetrics batchExecutionMetrics;
    private final Clock clock;
    private final boolean enabled;
    private final Duration threshold;

    @Autowired
    public StaleJobExecutionRecovery(BatchGuard batchGuard,
                                     JobExplorer jobExplorer,
                                     JobRepository jobRepository,
                                     BatchExecutionMetrics batchExecutionMetrics,
                                     @Value("${batch.recovery.stale.enabled:true}") boolean enabled,
                                     @Value("${batch.recovery.stale.threshold:PT15M}") Duration threshold) {
        this(batchGuard, jobExplorer, jobRepository, batchExecutionMetrics,
                Clock.systemDefaultZone(), enabled, threshold);
    }

    /** 테스트에서 고정 {@link Clock} 을 넣기 위한 생성자. */
    public StaleJobExecutionRecovery(BatchGuard batchGuard,
                                     JobExplorer jobExplorer,
                                     JobRepository jobRepository,
                                     BatchExecutionMetrics batchExecutionMetrics,
                                     Clock clock,
                                     boolean enabled,
                                     Duration threshold) {
        this.batchGuard = batchGuard;
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.batchExecutionMetrics = batchExecutionMetrics;
        this.clock = clock;
        this.enabled = enabled;
        this.threshold = threshold;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(ORDER)
    public void recoverStaleExecutions() {
        if (!enabled) {
            log.info("[batch] batch.recovery.stale.enabled=false - 고아 실행을 정리하지 않는다.");
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (RunningJobExecution running : batchGuard.findRunningExecutions()) {
            Duration idle = running.idleFor(now);
            if (idle.compareTo(threshold) < 0) {
                log.warn("[batch] job={} executionId={} 진행 중으로 본다 - 정리하지 않는다. idle={}s threshold={}s",
                        running.jobName(), running.jobExecutionId(), idle.toSeconds(), threshold.toSeconds());
                continue;
            }
            recover(running, idle, now);
        }
    }

    /** 스텝을 먼저 닫는다. STARTED 로 남은 스텝은 재시작 판정을 어지럽힌다. */
    private void recover(RunningJobExecution running, Duration idle, LocalDateTime now) {
        JobExecution execution = jobExplorer.getJobExecution(running.jobExecutionId());
        if (execution == null || execution.getEndTime() != null) {
            return;
        }

        String description = "%s: idle=%ds threshold=%ds".formatted(MARKER, idle.toSeconds(), threshold.toSeconds());
        try {
            int closedSteps = 0;
            for (StepExecution step : execution.getStepExecutions()) {
                if (step.getEndTime() != null) {
                    continue;
                }
                step.setStatus(BatchStatus.FAILED);
                step.setExitStatus(ExitStatus.FAILED.addExitDescription(description));
                step.setEndTime(now);
                jobRepository.update(step);
                closedSteps++;
            }

            execution.setStatus(BatchStatus.FAILED);
            execution.setExitStatus(ExitStatus.FAILED.addExitDescription(description));
            execution.setEndTime(now);
            jobRepository.update(execution);

            batchExecutionMetrics.staleRecovered(running.jobName());
            log.warn("[batch] 고아 실행 정리 job={} executionId={} idle={}분 startedAt={} steps={}",
                    running.jobName(), running.jobExecutionId(), idle.toMinutes(),
                    running.startedAt(), closedSteps);

        } catch (RuntimeException e) {
            log.warn("[batch] 고아 실행 정리 실패 job={} executionId={} reason={}",
                    running.jobName(), running.jobExecutionId(), e.getMessage());
        }
    }
}
