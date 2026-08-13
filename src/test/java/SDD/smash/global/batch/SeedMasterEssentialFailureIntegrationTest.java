package SDD.smash.global.batch;

import SDD.smash.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 필수 기준 데이터가 실패했을 때의 동작을 검증한다.
 *
 * <p>{@code seed.master.enabled=true} 라 컨텍스트가 뜨면서 {@link SeedMasterJobLauncher} 가 실제로 돈다.
 * 즉 "기동 → 시드 실패 → 준비 완료 아님" 경로 전체가 이 테스트의 대상이다.
 * <b>애플리케이션 컨텍스트는 죽지 않아야 한다</b> — 죽이면 컨테이너가 재시작 루프에 빠진다.
 */
@TestPropertySource(properties = {
        "seed.master.enabled=true",
        "seed.version=essential-failure",
        "sido.filePath=src/test/resources/seed/does-not-exist.csv",
        "sigungu.filePath=src/test/resources/seed/sigungu.csv",
        "jobCodeTop.filePath=src/test/resources/seed/level_top.csv",
        "jobCodeMiddle.filePath=src/test/resources/seed/level_middle.csv",
        "apis.molit.service-key="
})
class SeedMasterEssentialFailureIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SeedReadiness seedReadiness;

    @Autowired
    private JobExplorer jobExplorer;

    @Test
    @DisplayName("필수 기준 데이터가 실패하면 애플리케이션을 준비 완료로 처리하지 않는다")
    void doesNotBecomeReadyWhenEssentialSeedFails() {
        assertThat(seedReadiness.isReady()).isFalse();
        assertThat(seedReadiness.notReadyReason()).isPresent();
        assertThat(seedReadiness.notReadyReason().orElseThrow()).contains("SidoStep");
    }

    @Test
    @DisplayName("필수 Step 이 실패하면 Job 을 FAILED 로 끝내고 뒤 Step 을 실행하지 않는다")
    void failsJobAndStopsBeforeFollowingSteps() {
        JobExecution execution = lastSeedMasterExecution();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getStepExecutions()).hasSize(1);

        StepExecution failed = execution.getStepExecutions().iterator().next();
        assertThat(failed.getStepName()).isEqualTo("SidoStep");
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitDescription()).contains("필수 기준 데이터 적재 실패");
    }

    private JobExecution lastSeedMasterExecution() {
        List<JobInstance> instances =
                jobExplorer.getJobInstances(SeedMasterJobConfig.SEED_MASTER_JOB, 0, 50);
        return instances.stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .filter(execution -> "essential-failure".equals(
                        execution.getJobParameters().getString(SeedStepSpec.SEED_VERSION)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seedMasterJob 실행 이력이 없다"));
    }
}
