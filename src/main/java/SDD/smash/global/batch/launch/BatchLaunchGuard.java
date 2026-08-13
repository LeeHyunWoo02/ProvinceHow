package SDD.smash.global.batch.launch;

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
 *       빠르고 로그가 명확하지만 <b>확인과 기동 사이에 틈(TOCTOU)이 있어 이것만으로는 부족하다</b>.</li>
 *   <li>결정적 JobParameters — 기동할 때 {@code triggerTime} 같은 매 실행마다 달라지는 값을
 *       넣지 않는다. 그래야 같은 기준일/기준월의 실행이 <b>같은 JobInstance</b> 로 수렴한다.</li>
 *   <li>배치 메타의 {@code JOB_INSTANCE(JOB_NAME, JOB_KEY)} 유니크 제약 — 2번 덕분에 두 인스턴스가
 *       동시에 같은 Job 을 기동하면 뒤늦은 쪽의 {@code createJobExecution} 이 DB 레벨에서 막힌다.
 *       {@code JobExecutionAlreadyRunningException} / {@code JobInstanceAlreadyCompleteException}
 *       를 잡아 건너뜀으로 처리한다.</li>
 * </ol>
 *
 * <p>즉 멀티 인스턴스 경쟁은 <b>JVM 메모리 락이 아니라 배치 메타 DB의 유니크 제약</b>이 막는다.
 * 다만 이 보호는 "JobParameters 가 같을 때"만 성립한다 — 서로 다른 파라미터로 같은 테이블을
 * 동시에 적재하는 경우까지 막으려면 전용 락 테이블이 필요하다(스키마 변경이므로 여기서는 하지 않는다).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchLaunchGuard {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;

    public BatchLaunchResult launch(Job job, JobParameters jobParameters) {
        String jobName = job.getName();

        if (isRunning(jobName)) {
            String reason = "이전 실행이 아직 진행 중이다";
            log.warn("[batch] job={} 기동 건너뜀 reason={}", jobName, reason);
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
            return BatchLaunchResult.of(BatchLaunchResult.Status.SKIPPED_RUNNING, reason);

        } catch (JobInstanceAlreadyCompleteException e) {
            String reason = "같은 JobParameters 로 이미 완료됐다";
            log.info("[batch] job={} 기동 건너뜀 reason={} params={}", jobName, reason, jobParameters);
            return BatchLaunchResult.of(BatchLaunchResult.Status.SKIPPED_ALREADY_COMPLETE, reason);

        } catch (JobRestartException e) {
            log.error("[batch] job={} 재시작 불가", jobName, e);
            return BatchLaunchResult.of(BatchLaunchResult.Status.REJECTED, e.getMessage());

        } catch (Exception e) {
            log.error("[batch] job={} 기동 실패", jobName, e);
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
