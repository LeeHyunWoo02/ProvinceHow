package SDD.smash.global.batch.launch;

import SDD.smash.global.metrics.BatchExecutionMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Component;

/**
 * 배치 기동의 단일 창구. <b>중복 실행을 막고, 어떤 경우에도 예외를 밖으로 흘리지 않는다.</b>
 *
 * <p><b>중복 실행 제어 3단</b>
 * <ol>
 *   <li>{@code jobExplorer.findRunningJobExecutions(jobName)} — 진행 중이면 기동하지 않는다.
 *       조건이 {@code JOB_NAME = ? AND END_TIME IS NULL} 이라 JobInstance 와 무관하게 막는다.
 *       바꿔 말해 <b>죽은 JVM 이 남긴 행 하나가 이후 모든 기동을 영구히 막는다</b> —
 *       그 행을 닫는 것이 {@code StaleJobExecutionRecovery} 다.</li>
 *   <li>결정적 JobParameters — 기동할 때 {@code triggerTime} 같은 매 실행마다 달라지는 값을
 *       넣지 않는다. 그래야 같은 기준일/기준월의 실행이 <b>같은 JobInstance</b> 로 수렴한다.</li>
 *   <li>배치 메타의 {@code JOB_INSTANCE(JOB_NAME, JOB_KEY)} 유니크 제약 — 2번 덕분에 같은 Job 을
 *       중복 기동하면 뒤늦은 쪽의 {@code createJobExecution} 이 DB 레벨에서 막힌다.
 *       {@code JobExecutionAlreadyRunningException} / {@code JobInstanceAlreadyCompleteException}
 *       를 잡아 건너뜀으로 처리한다.</li>
 * </ol>
 *
 * <p><b>이 프로젝트는 단일 인스턴스를 전제한다.</b> 배치 가드는 위 3단으로 동시 기동을 막지만,
 * 그 앞에서 먼저 깨지는 것이 있다 — 외부 API 일일 호출 예산({@code MolitRentApiAdapter} /
 * {@code LocalDataApiAdapter} 의 {@code callsUsedToday}·{@code budgetDate})과 동시 호출·최소 간격
 * 제어({@code Semaphore}, {@code nextAllowedAtMillis})가 전부 <b>JVM 메모리</b>에 있다.
 * 인스턴스가 둘이면 각자 절반만 썼다고 세면서 실제로는 두 배를 쏴 실한도를 조용히 넘는다.
 * 고아 실행 정리도 이 전제 위에 서 있다(다만 나이 임계와 스텝 하트비트 판정이 있어 진행 중인
 * 실행은 건드리지 않는다). 멀티로 가야 한다면 <b>배치를 인스턴스 하나에서만 켜는 것</b>이 먼저이고
 * ({@code SEED_MASTER_ENABLED}·{@code *_BATCH_ENABLED} 가 이미 환경변수다), 예산 카운터를
 * Redis 로 옮기는 것은 그다음이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchLaunchGuard {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final BatchExecutionMetrics batchExecutionMetrics;

    public BatchLaunchResult launch(Job job, JobParameters jobParameters) {
        String jobName = job.getName();

        if (isRunning(jobName)) {
            String reason = "이전 실행이 아직 진행 중이다";
            log.warn("[batch] job={} 기동 건너뜀 reason={}", jobName, reason);
            batchExecutionMetrics.launchSkipped(jobName, "running");
            return BatchLaunchResult.of(BatchLaunchResult.Status.SKIPPED_RUNNING, reason);
        }

        try {
            JobExecution execution = jobLauncher.run(job, jobParameters);
            log.info("[batch] job={} 기동 executionId={} status={} params={}",
                    jobName, execution.getId(), execution.getStatus(), jobParameters);
            return BatchLaunchResult.launched(execution);

        } catch (JobExecutionAlreadyRunningException e) {
            String reason = "다른 인스턴스가 같은 JobInstance 를 실행 중이다";
            log.warn("[batch] job={} 기동 건너뜀 reason={}", jobName, reason);
            batchExecutionMetrics.launchSkipped(jobName, "running_other_instance");
            return BatchLaunchResult.of(BatchLaunchResult.Status.SKIPPED_RUNNING, reason);

        // 같은 파라미터로 이미 완료된 경우는 정상 멱등이라 세지 않는다.
        } catch (JobInstanceAlreadyCompleteException e) {
            String reason = "같은 JobParameters 로 이미 완료됐다";
            log.info("[batch] job={} 기동 건너뜀 reason={} params={}", jobName, reason, jobParameters);
            return BatchLaunchResult.of(BatchLaunchResult.Status.SKIPPED_ALREADY_COMPLETE, reason);

        } catch (JobRestartException e) {
            log.error("[batch] job={} 재시작 불가", jobName, e);
            batchExecutionMetrics.launchSkipped(jobName, "restart_rejected");
            return BatchLaunchResult.of(BatchLaunchResult.Status.REJECTED, e.getMessage());

        } catch (Exception e) {
            log.error("[batch] job={} 기동 실패", jobName, e);
            batchExecutionMetrics.launchSkipped(jobName, "launch_failed");
            return BatchLaunchResult.of(BatchLaunchResult.Status.FAILED, e.getMessage());
        }
    }

    public boolean isRunning(String jobName) {
        try {
            return !jobExplorer.findRunningJobExecutions(jobName).isEmpty();
        } catch (RuntimeException e) {
            log.warn("[batch] job={} 실행 여부 조회 실패 - 실행 중이 아닌 것으로 본다. reason={}",
                    jobName, e.getMessage());
            return false;
        }
    }
}
