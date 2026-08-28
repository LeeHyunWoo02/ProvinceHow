package SDD.smash.global.batch.recovery;

import SDD.smash.global.batch.launch.BatchGuard;
import SDD.smash.global.batch.launch.RunningJobExecution;
import SDD.smash.global.metrics.BatchExecutionMetrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StaleJobExecutionRecoveryTest {

    private static final Duration THRESHOLD = Duration.ofMinutes(15);
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Instant NOW = Instant.parse("2026-08-25T03:00:00Z");

    @Mock BatchGuard batchGuard;
    @Mock JobExplorer jobExplorer;
    @Mock JobRepository jobRepository;
    @Mock BatchExecutionMetrics batchExecutionMetrics;

    private final Clock clock = Clock.fixed(NOW, ZONE);

    @Test
    @DisplayName("임계를 넘긴 고아 실행은 스텝을 먼저 닫고 JobExecution 을 FAILED 로 정리한다")
    void closesStepBeforeJobExecutionWhenIdleExceedsThreshold() {
        // given
        JobExecution execution = runningExecution();
        StepExecution step = execution.getStepExecutions().iterator().next();
        given(batchGuard.findRunningExecutions()).willReturn(List.of(stale(THRESHOLD.plusSeconds(1))));
        given(jobExplorer.getJobExecution(11L)).willReturn(execution);

        // when
        recovery(true, THRESHOLD).recoverStaleExecutions();

        // then
        assertThat(step.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(step.getEndTime()).isEqualTo(now());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getEndTime()).isEqualTo(now());
        assertThat(execution.getExitStatus().getExitDescription()).contains("STALE_RECOVERED");

        InOrder order = inOrder(jobRepository);
        order.verify(jobRepository).update(step);
        order.verify(jobRepository).update(execution);
        then(batchExecutionMetrics).should().staleRecovered("infraJob");
    }

    @Test
    @DisplayName("임계 직전의 실행은 살아 있는 것으로 보고 건드리지 않는다")
    void leavesExecutionAloneJustBelowThreshold() {
        given(batchGuard.findRunningExecutions()).willReturn(List.of(stale(THRESHOLD.minusSeconds(1))));

        recovery(true, THRESHOLD).recoverStaleExecutions();

        then(jobExplorer).shouldHaveNoInteractions();
        then(jobRepository).shouldHaveNoInteractions();
        then(batchExecutionMetrics).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 끝난 실행이면 아무것도 바꾸지 않는다")
    void skipsExecutionThatFinishedMeanwhile() {
        JobExecution finished = runningExecution();
        finished.setStatus(BatchStatus.COMPLETED);
        finished.setEndTime(now());
        given(batchGuard.findRunningExecutions()).willReturn(List.of(stale(Duration.ofHours(3))));
        given(jobExplorer.getJobExecution(11L)).willReturn(finished);

        recovery(true, THRESHOLD).recoverStaleExecutions();

        then(jobRepository).shouldHaveNoInteractions();
        then(batchExecutionMetrics).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("batch.recovery.stale.enabled=false 면 조회조차 하지 않는다")
    void doesNothingWhenDisabled() {
        recovery(false, THRESHOLD).recoverStaleExecutions();

        then(batchGuard).shouldHaveNoInteractions();
        then(jobRepository).shouldHaveNoInteractions();
    }

    private StaleJobExecutionRecovery recovery(boolean enabled, Duration threshold) {
        return new StaleJobExecutionRecovery(batchGuard, jobExplorer, jobRepository,
                batchExecutionMetrics, clock, enabled, threshold);
    }

    /** 마지막 하트비트가 {@code idle} 만큼 지난 실행. */
    private RunningJobExecution stale(Duration idle) {
        return new RunningJobExecution(11L, "infraJob", now().minusHours(5), now().minus(idle));
    }

    private JobExecution runningExecution() {
        JobExecution execution = new JobExecution(11L, new JobParameters());
        execution.setStatus(BatchStatus.STARTED);
        StepExecution step = execution.createStepExecution("infraCollectStep");
        step.setId(21L);
        step.setStatus(BatchStatus.STARTED);
        return execution;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
