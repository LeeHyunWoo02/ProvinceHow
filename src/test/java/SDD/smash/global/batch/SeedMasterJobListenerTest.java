package SDD.smash.global.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SeedMasterJobListenerTest {

    @Mock
    JobRepository jobRepository;

    private final List<SeedStepSpec> specs = List.of(
            spec("SidoStep", SeedGroup.ESSENTIAL),
            spec("populationStep", SeedGroup.EXTERNAL));

    @Test
    @DisplayName("필수 기준 데이터가 실패하면 실패한 Step 이름을 사유로 남긴다")
    void recordsFailedEssentialSteps() {
        SeedMasterJobListener listener = new SeedMasterJobListener(specs, jobRepository);
        JobExecution jobExecution = jobExecution();
        failedStep(jobExecution, "SidoStep");
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.setExitStatus(ExitStatus.FAILED);

        listener.afterJob(jobExecution);

        assertThat(listener.failedEssentialSteps(jobExecution)).containsExactly("SidoStep");
        assertThat(jobExecution.getExitStatus().getExitDescription()).contains("필수 기준 데이터 적재 실패");
        then(jobRepository).should().updateExecutionContext(jobExecution);
    }

    @Test
    @DisplayName("외부 갱신 데이터만 실패하면 exitCode 를 낮춰 필수 실패와 구분한다")
    void downgradesExitCodeWhenOnlyExternalStepFailed() {
        SeedMasterJobListener listener = new SeedMasterJobListener(specs, jobRepository);
        JobExecution jobExecution = jobExecution();
        completedStep(jobExecution, "SidoStep");
        failedStep(jobExecution, "populationStep");
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        listener.afterJob(jobExecution);

        assertThat(listener.failedEssentialSteps(jobExecution)).isEmpty();
        assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(SeedMasterJobListener.COMPLETED_WITH_EXTERNAL_FAILURES);
        assertThat(jobExecution.getExitStatus().getExitDescription()).contains("populationStep");
    }

    @Test
    @DisplayName("건너뛴 Step 의 사유를 stepName 별로 모아 준다")
    void collectsSkipReasonsByStepName() {
        SeedMasterJobListener listener = new SeedMasterJobListener(specs, jobRepository);
        JobExecution jobExecution = jobExecution();
        jobExecution.getExecutionContext()
                .putString(SeedStepGate.SKIP_REASON_PREFIX + "populationStep", "필수 설정 누락 keys=[population.filePath]");
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        listener.afterJob(jobExecution);

        assertThat(listener.skipReasons(jobExecution))
                .containsOnlyKeys("populationStep")
                .containsEntry("populationStep", "필수 설정 누락 keys=[population.filePath]");
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }

    private JobExecution jobExecution() {
        return new JobExecution(1L, new JobParameters());
    }

    private void failedStep(JobExecution jobExecution, String stepName) {
        StepExecution stepExecution = jobExecution.createStepExecution(stepName);
        stepExecution.setStatus(BatchStatus.FAILED);
    }

    private void completedStep(JobExecution jobExecution, String stepName) {
        StepExecution stepExecution = jobExecution.createStepExecution(stepName);
        stepExecution.setStatus(BatchStatus.COMPLETED);
    }

    private SeedStepSpec spec(String stepName, SeedGroup group) {
        return new SeedStepSpec(stepName, group, true, SeedStepSpec.SEED_VERSION, Map.of(), List.of(), null);
    }
}
