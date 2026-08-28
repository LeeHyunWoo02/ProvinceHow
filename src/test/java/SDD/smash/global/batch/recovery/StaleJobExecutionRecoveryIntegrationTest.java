package SDD.smash.global.batch.recovery;

import SDD.smash.IntegrationTestSupport;
import SDD.smash.global.batch.launch.BatchLaunchGuard;
import SDD.smash.global.batch.launch.BatchLaunchResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재배포가 죽인 실행(= {@code END_TIME IS NULL} 로 남은 행)이 이후 기동을 영구히 막던 사고의 회귀 테스트.
 * <b>Docker 데몬이 떠 있어야 한다.</b>
 *
 * <p>JVM 을 죽일 필요가 없다 — 좀비의 정의가 그 행이므로 {@code createJobExecution} 으로 만들고
 * 끝내지 않으면 그대로 재현된다. 나이는 {@code STEP_EXECUTION.LAST_UPDATED} 를 과거로 밀어 만든다.
 */
class StaleJobExecutionRecoveryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private StaleJobExecutionRecovery staleJobExecutionRecovery;

    @Autowired
    private BatchLaunchGuard batchLaunchGuard;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    @Qualifier("batchDataSource")
    private DataSource metaDataSource;

    @Test
    @DisplayName("정리 전에는 기동이 막히고 정리 후에는 다시 기동한다")
    void unblocksLaunchThatStaleExecutionHadBlockedForever() throws Exception {
        // given - 직전 배포가 남긴 좀비 (스텝 하트비트가 3시간 전에서 멈췄다)
        JobExecution zombie = zombie("staleRecoveryBlockedJob", "before");
        backdateHeartbeat(zombie, LocalDateTime.now().minusHours(3));

        SimpleJob job = probeJob("staleRecoveryBlockedJob");
        assertThat(batchLaunchGuard.launch(job, parametersOf("after")).status())
                .isEqualTo(BatchLaunchResult.Status.SKIPPED_RUNNING);

        // when
        staleJobExecutionRecovery.recoverStaleExecutions();

        // then - 8/25 사고 그 자체다. 이 한 쌍이 회귀의 본질이다.
        assertThat(batchLaunchGuard.launch(job, parametersOf("after")).isLaunched()).isTrue();
    }

    @Test
    @DisplayName("정리한 실행과 스텝에 FAILED, 종료 시각, STALE_RECOVERED 마커가 남는다")
    void marksRecoveredExecutionAndStep() throws Exception {
        JobExecution zombie = zombie("staleRecoveryMarkerJob", "marker");
        long stepExecutionId = backdateHeartbeat(zombie, LocalDateTime.now().minusHours(2));

        staleJobExecutionRecovery.recoverStaleExecutions();

        Map<String, Object> jobRow = jdbcTemplate().queryForMap(
                "SELECT STATUS, END_TIME, EXIT_CODE, EXIT_MESSAGE FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?",
                zombie.getId());
        assertThat(jobRow.get("STATUS")).isEqualTo(BatchStatus.FAILED.name());
        assertThat(jobRow.get("END_TIME")).isNotNull();
        assertThat(String.valueOf(jobRow.get("EXIT_MESSAGE"))).contains("STALE_RECOVERED");

        Map<String, Object> stepRow = jdbcTemplate().queryForMap(
                "SELECT STATUS, END_TIME, EXIT_MESSAGE FROM BATCH_STEP_EXECUTION WHERE STEP_EXECUTION_ID = ?",
                stepExecutionId);
        assertThat(stepRow.get("STATUS")).isEqualTo(BatchStatus.FAILED.name());
        assertThat(stepRow.get("END_TIME")).isNotNull();
        assertThat(String.valueOf(stepRow.get("EXIT_MESSAGE"))).contains("STALE_RECOVERED");
    }

    @Test
    @DisplayName("방금 시작한 실행은 임계에 못 미쳐 정리하지 않는다")
    void keepsFreshExecutionUntouched() throws Exception {
        JobExecution fresh = zombie("staleRecoveryFreshJob", "fresh");
        backdateHeartbeat(fresh, LocalDateTime.now());
        try {
            staleJobExecutionRecovery.recoverStaleExecutions();

            Map<String, Object> jobRow = jdbcTemplate().queryForMap(
                    "SELECT STATUS, END_TIME FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", fresh.getId());
            assertThat(jobRow.get("STATUS")).isNotEqualTo(BatchStatus.FAILED.name());
            assertThat(jobRow.get("END_TIME")).isNull();
        } finally {
            close(fresh);
        }
    }

    /** 끝내지 않은 JobExecution + StepExecution. 이것이 좀비다. */
    private JobExecution zombie(String jobName, String marker) throws Exception {
        JobExecution execution = jobRepository.createJobExecution(jobName, parametersOf(marker));
        execution.setStatus(BatchStatus.STARTED);
        execution.setStartTime(LocalDateTime.now().minusHours(5));
        jobRepository.update(execution);

        StepExecution step = execution.createStepExecution(jobName + "Step");
        step.setStatus(BatchStatus.STARTED);
        jobRepository.add(step);
        return execution;
    }

    /** 스텝의 하트비트를 과거로 민다. Job 행의 LAST_UPDATED 는 하트비트가 아니므로 건드리지 않는다. */
    private long backdateHeartbeat(JobExecution execution, LocalDateTime heartbeatAt) {
        StepExecution step = execution.getStepExecutions().iterator().next();
        jdbcTemplate().update(
                "UPDATE BATCH_STEP_EXECUTION SET LAST_UPDATED = ?, START_TIME = ? WHERE STEP_EXECUTION_ID = ?",
                heartbeatAt, heartbeatAt, step.getId());
        return step.getId();
    }

    private void close(JobExecution execution) {
        jdbcTemplate().update(
                "UPDATE BATCH_STEP_EXECUTION SET STATUS = 'ABANDONED', END_TIME = ? WHERE JOB_EXECUTION_ID = ?",
                LocalDateTime.now(), execution.getId());
        jdbcTemplate().update(
                "UPDATE BATCH_JOB_EXECUTION SET STATUS = 'ABANDONED', END_TIME = ? WHERE JOB_EXECUTION_ID = ?",
                LocalDateTime.now(), execution.getId());
    }

    private SimpleJob probeJob(String jobName) {
        SimpleJob job = new SimpleJob(jobName);
        job.setJobRepository(jobRepository);
        return job;
    }

    private JobParameters parametersOf(String marker) {
        return new JobParametersBuilder().addString("marker", marker).toJobParameters();
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(metaDataSource);
    }
}
