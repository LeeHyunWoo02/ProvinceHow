package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 반영에 성공한 회차만 staging 을 정리한다. 며칠에 걸쳐 모은 수집분을 실수로 지우면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class InfraStagingCleanupListenerTest {

    private static final String RUN_KEY = "2026-08-19";

    @Mock
    private InfraCollectionStagingStore stagingStore;

    @Test
    @DisplayName("회차가 미완성이라 반영을 건너뛴 실행에서는 staging 을 지우지 않는다")
    void keepsStagingWhenNothingWasApplied() {
        StepExecution stepExecution = stepExecution(BatchStatus.COMPLETED);

        new InfraStagingCleanupListener(stagingStore).afterStep(stepExecution);

        then(stagingStore).should(never()).purge(RUN_KEY);
    }

    @Test
    @DisplayName("반영에 성공하면 그 회차의 staging 을 정리한다")
    void purgesAppliedRunAfterSuccessfulStep() {
        StepExecution stepExecution = stepExecution(BatchStatus.COMPLETED);
        stepExecution.getExecutionContext()
                .putString(InfraStagingCleanupListener.CTX_APPLIED_RUN_KEY, RUN_KEY);

        new InfraStagingCleanupListener(stagingStore).afterStep(stepExecution);

        then(stagingStore).should().purge(RUN_KEY);
    }

    @Test
    @DisplayName("Step 이 실패하면 반영 표시가 있어도 staging 을 지키지 않고 남긴다")
    void keepsStagingWhenStepFailed() {
        StepExecution stepExecution = stepExecution(BatchStatus.FAILED);
        stepExecution.getExecutionContext()
                .putString(InfraStagingCleanupListener.CTX_APPLIED_RUN_KEY, RUN_KEY);

        new InfraStagingCleanupListener(stagingStore).afterStep(stepExecution);

        then(stagingStore).should(never()).purge(RUN_KEY);
    }

    private static StepExecution stepExecution(BatchStatus status) {
        JobExecution jobExecution = new JobExecution(new JobInstance(1L, "infraJob"), new JobParameters());
        StepExecution stepExecution = new StepExecution("infraStep", jobExecution);
        stepExecution.setStatus(status);
        return stepExecution;
    }
}
