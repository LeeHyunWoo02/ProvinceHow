package SDD.smash.global.batch.listener;

import SDD.smash.global.batch.seed.SeedGroup;
import SDD.smash.global.batch.seed.SeedStepGate;
import SDD.smash.global.batch.seed.SeedStepSpec;

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
    @DisplayName("외부 갱신 Step 이 ABANDONED 여도 실패로 집계해 exitCode 를 낮춘다")
    void downgradesExitCodeWhenExternalStepAbandoned() {
        // given - 흐름상 마지막이 아닌 EXTERNAL Step 이 실패하면 Spring Batch 가 ABANDONED 로 올린다
        SeedMasterJobListener listener = new SeedMasterJobListener(specs, jobRepository);
        JobExecution jobExecution = jobExecution();
        completedStep(jobExecution, "SidoStep");
        abandonedStep(jobExecution, "populationStep");
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        // when
        listener.afterJob(jobExecution);

        // then
        assertThat(listener.failedExternalSteps(jobExecution)).containsExactly("populationStep");
        assertThat(listener.failedEssentialSteps(jobExecution)).isEmpty();
        assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(SeedMasterJobListener.COMPLETED_WITH_EXTERNAL_FAILURES);
        assertThat(jobExecution.getExitStatus().getExitDescription()).contains("populationStep");
    }

    @Test
    @DisplayName("중간 외부 Step 만 ABANDONED 면 뒤 Step 이 성공해도 완료로 보고하지 않는다")
    void doesNotReportCompletedWhenMiddleExternalStepAbandoned() {
        // given - 운영 실측(JOB_EXECUTION_ID=2): population COMPLETED / infra ABANDONED / dwelling COMPLETED
        List<SeedStepSpec> flowSpecs = List.of(
                spec("populationStep", SeedGroup.EXTERNAL),
                spec("infraStep", SeedGroup.EXTERNAL),
                spec("dwellingStep", SeedGroup.EXTERNAL));
        SeedMasterJobListener listener = new SeedMasterJobListener(flowSpecs, jobRepository);
        JobExecution jobExecution = jobExecution();
        completedStep(jobExecution, "populationStep");
        abandonedStep(jobExecution, "infraStep");
        completedStep(jobExecution, "dwellingStep");
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        // when
        listener.afterJob(jobExecution);

        // then
        assertThat(listener.failedExternalSteps(jobExecution)).containsExactly("infraStep");
        assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(SeedMasterJobListener.COMPLETED_WITH_EXTERNAL_FAILURES);
    }

    @Test
    @DisplayName("필수 기준 Step 이 ABANDONED 여도 필수 실패로 집계한다")
    void recordsAbandonedEssentialStepAsFailure() {
        // given
        SeedMasterJobListener listener = new SeedMasterJobListener(specs, jobRepository);
        JobExecution jobExecution = jobExecution();
        abandonedStep(jobExecution, "SidoStep");
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        // when
        listener.afterJob(jobExecution);

        // then
        assertThat(listener.failedEssentialSteps(jobExecution)).containsExactly("SidoStep");
        assertThat(jobExecution.getExitStatus().getExitDescription()).contains("필수 기준 데이터 적재 실패");
    }

    @Test
    @DisplayName("모든 Step 이 COMPLETED 면 exitStatus 를 낮추지 않고 완료로 남긴다")
    void keepsCompletedExitStatusWhenAllStepsCompleted() {
        // given
        SeedMasterJobListener listener = new SeedMasterJobListener(specs, jobRepository);
        JobExecution jobExecution = jobExecution();
        completedStep(jobExecution, "SidoStep");
        completedStep(jobExecution, "populationStep");
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        // when
        listener.afterJob(jobExecution);

        // then
        assertThat(listener.failedEssentialSteps(jobExecution)).isEmpty();
        assertThat(listener.failedExternalSteps(jobExecution)).isEmpty();
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(jobExecution.getExitStatus().getExitDescription()).isEmpty();
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

    /** 실패한 Step 을 지나쳐 흐름이 이어질 때 Spring Batch 가 남기는 상태 (ExitStatus 는 FAILED 로 남는다). */
    private void abandonedStep(JobExecution jobExecution, String stepName) {
        StepExecution stepExecution = jobExecution.createStepExecution(stepName);
        stepExecution.setStatus(BatchStatus.ABANDONED);
        stepExecution.setExitStatus(ExitStatus.FAILED);
    }

    private void completedStep(JobExecution jobExecution, String stepName) {
        StepExecution stepExecution = jobExecution.createStepExecution(stepName);
        stepExecution.setStatus(BatchStatus.COMPLETED);
    }

    private SeedStepSpec spec(String stepName, SeedGroup group) {
        return new SeedStepSpec(stepName, group, true, SeedStepSpec.SEED_VERSION, Map.of(), List.of(), null);
    }
}
