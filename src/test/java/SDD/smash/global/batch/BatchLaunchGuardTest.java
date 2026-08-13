package SDD.smash.global.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class BatchLaunchGuardTest {

    @Mock
    JobLauncher jobLauncher;

    @Mock
    JobExplorer jobExplorer;

    @Mock
    Job job;

    private final JobParameters parameters = new JobParametersBuilder()
            .addString(SeedStepSpec.BASE_DATE, "2026-08-13")
            .toJobParameters();

    @Test
    @DisplayName("이전 실행이 진행 중이면 기동하지 않는다")
    void skipsWhenPreviousExecutionIsStillRunning() throws Exception {
        given(job.getName()).willReturn("dwellingJob");
        given(jobExplorer.findRunningJobExecutions("dwellingJob"))
                .willReturn(Set.of(new JobExecution(1L, parameters)));

        BatchLaunchResult result = new BatchLaunchGuard(jobLauncher, jobExplorer).launch(job, parameters);

        assertThat(result.status()).isEqualTo(BatchLaunchResult.Status.SKIPPED_RUNNING);
        then(jobLauncher).should(org.mockito.Mockito.never()).run(any(), any());
    }

    @Test
    @DisplayName("다른 인스턴스가 같은 JobInstance 를 실행 중이면 예외를 흡수하고 건너뛴다")
    void absorbsAlreadyRunningExceptionFromAnotherInstance() throws Exception {
        given(job.getName()).willReturn("dwellingJob");
        given(jobExplorer.findRunningJobExecutions("dwellingJob")).willReturn(Set.of());
        willThrow(new JobExecutionAlreadyRunningException("running"))
                .given(jobLauncher).run(job, parameters);

        BatchLaunchResult result = new BatchLaunchGuard(jobLauncher, jobExplorer).launch(job, parameters);

        assertThat(result.status()).isEqualTo(BatchLaunchResult.Status.SKIPPED_RUNNING);
    }

    @Test
    @DisplayName("같은 JobParameters 로 이미 완료됐으면 멱등하게 건너뛴다")
    void skipsWhenSameParametersAlreadyCompleted() throws Exception {
        given(job.getName()).willReturn("dwellingJob");
        given(jobExplorer.findRunningJobExecutions("dwellingJob")).willReturn(Set.of());
        willThrow(new JobInstanceAlreadyCompleteException("done"))
                .given(jobLauncher).run(job, parameters);

        BatchLaunchResult result = new BatchLaunchGuard(jobLauncher, jobExplorer).launch(job, parameters);

        assertThat(result.status()).isEqualTo(BatchLaunchResult.Status.SKIPPED_ALREADY_COMPLETE);
    }

    @Test
    @DisplayName("기동 중 예외가 나도 밖으로 던지지 않는다")
    void neverPropagatesLaunchFailure() throws Exception {
        given(job.getName()).willReturn("dwellingJob");
        given(jobExplorer.findRunningJobExecutions("dwellingJob")).willReturn(Set.of());
        willThrow(new IllegalStateException("boom")).given(jobLauncher).run(job, parameters);

        BatchLaunchResult result = new BatchLaunchGuard(jobLauncher, jobExplorer).launch(job, parameters);

        assertThat(result.status()).isEqualTo(BatchLaunchResult.Status.FAILED);
        assertThat(result.reason()).isEqualTo("boom");
    }

    @Test
    @DisplayName("정상 기동하면 JobExecution 을 돌려준다")
    void returnsExecutionWhenLaunched() throws Exception {
        JobExecution execution = new JobExecution(7L, parameters);
        given(job.getName()).willReturn("dwellingJob");
        given(jobExplorer.findRunningJobExecutions("dwellingJob")).willReturn(Set.of());
        given(jobLauncher.run(job, parameters)).willReturn(execution);

        BatchLaunchResult result = new BatchLaunchGuard(jobLauncher, jobExplorer).launch(job, parameters);

        assertThat(result.isLaunched()).isTrue();
        assertThat(result.jobExecution()).contains(execution);
        assertThat(List.of(result.status())).containsExactly(BatchLaunchResult.Status.LAUNCHED);
    }
}
