package SDD.smash.global.batch.seed;

import SDD.smash.global.batch.launch.BatchLaunchGuard;
import SDD.smash.global.batch.launch.BatchLaunchResult;

import SDD.smash.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * seedMasterJob 이 FK 순서대로 Step 을 돌리는지, 조건을 못 채운 Step 을 어떻게 건너뛰는지를
 * 실제 MySQL(Testcontainers)에서 검증한다. <b>Docker 데몬이 떠 있어야 한다.</b>
 *
 * <p>기동 시 자동 실행은 {@code seed.master.enabled=false} 로 끄고 테스트가 직접 기동한다.
 * 외부 갱신 데이터의 소스(파일 경로 / API 키)는 비워 둬서 관문이 건너뛰도록 한다 —
 * <b>빈 키로 외부 API 를 호출하지 않는다</b>는 규칙 자체가 검증 대상이다.
 */
@TestPropertySource(properties = {
        "seed.master.enabled=false",
        "sido.filePath=src/test/resources/seed/sido.csv",
        "sigungu.filePath=src/test/resources/seed/sigungu.csv",
        "jobCodeTop.filePath=src/test/resources/seed/level_top.csv",
        "jobCodeMiddle.filePath=src/test/resources/seed/level_middle.csv",
        "apis.molit.service-key=",
        // industryStep 은 번들 YAML 마스터를 읽으므로 기본값이면 항상 실행된다.
        // 이 테스트는 "소스가 하나도 없는 환경" 을 만드는 것이 목적이라 위치도 비운다.
        "infra.industry-master.location="
})
class SeedMasterJobIntegrationTest extends IntegrationTestSupport {

    private static final List<String> ESSENTIAL_STEPS =
            List.of("SidoStep", "SigunguStep", "jcTopStep", "jcMiddleStep");
    // infraCollectStep 은 seed.jobs.infra-collect.enabled=false(기본) 라 기동 Job 의 관문에 없다.
    // 수 시간짜리 수집이 기동 Job 에 있으면 재배포 때마다 고아 실행이 생기기 때문이다.
    private static final List<String> EXTERNAL_STEPS =
            List.of("populationStep", "industryStep", "infraStep", "jobCountStep", "dwellingStep");

    @Autowired
    @Qualifier(SeedMasterJobConfig.SEED_MASTER_JOB)
    private Job seedMasterJob;

    @Autowired
    private BatchLaunchGuard batchLaunchGuard;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    @Qualifier("dataDBSource")
    private DataSource dataDataSource;

    @Test
    @DisplayName("필수 기준 데이터를 FK 선후관계대로 실행한다")
    void runsEssentialStepsInForeignKeyOrder() {
        JobExecution execution = launch("fk-order", "2026-01-01", "202601");

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(executedStepNames(execution)).containsExactlyElementsOf(ESSENTIAL_STEPS);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataDataSource);
        assertThat(countOf(jdbcTemplate, "sido")).isEqualTo(2);
        assertThat(countOf(jdbcTemplate, "sigungu")).isEqualTo(3);
        assertThat(countOf(jdbcTemplate, "job_code_top")).isEqualTo(2);
        assertThat(countOf(jdbcTemplate, "job_code_middle")).isEqualTo(2);
    }

    @Test
    @DisplayName("소스가 없는 외부 갱신 Step 은 실행하지 않고 사유를 남긴다")
    void recordsReasonForEveryExternalStepThatCannotRun() {
        JobExecution execution = launch("external-reason", "2026-01-02", "202601");

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(executedStepNames(execution)).doesNotContainAnyElementsOf(EXTERNAL_STEPS);

        Map<String, String> skipReasons = skipReasons(execution);
        assertThat(skipReasons).containsKeys(EXTERNAL_STEPS.toArray(new String[0]));
        assertThat(skipReasons.get("dwellingStep")).contains("apis.molit.service-key");
        assertThat(skipReasons.get("populationStep")).contains("apis.kosis.api-key");
    }

    @Test
    @DisplayName("SEED_VERSION 이 같으면 기준일이 바뀌어도 필수 기준 데이터를 다시 읽지 않는다")
    void doesNotRepeatEssentialStepsForSameSeedVersionOnAnotherBaseDate() {
        JobExecution first = launch("seed-version-guard", "2026-03-01", "202603");
        assertThat(executedStepNames(first)).containsExactlyElementsOf(ESSENTIAL_STEPS);

        JobExecution second = launch("seed-version-guard", "2026-03-02", "202603");

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getStepExecutions()).isEmpty();
        assertThat(skipReasons(second)).containsKeys(ESSENTIAL_STEPS.toArray(new String[0]));
        assertThat(skipReasons(second).get("SidoStep")).contains("이미 완료됨");
    }

    @Test
    @DisplayName("SEED_VERSION 이 올라가면 다시 적재하고 그 결과는 멱등하다")
    void reloadsIdempotentlyWhenSeedVersionChanges() {
        launch("idempotent-v1", "2026-04-01", "202604");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataDataSource);
        int sigunguBefore = countOf(jdbcTemplate, "sigungu");

        JobExecution second = launch("idempotent-v2", "2026-04-01", "202604");

        assertThat(executedStepNames(second)).containsExactlyElementsOf(ESSENTIAL_STEPS);
        assertThat(countOf(jdbcTemplate, "sigungu")).isEqualTo(sigunguBefore);
        assertThat(countOf(jdbcTemplate, "sido")).isEqualTo(2);
        assertThat(countOf(jdbcTemplate, "job_code_middle")).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 기준으로 다시 기동하면 멱등하게 건너뛴다")
    void skipsLaunchWhenSameParametersAlreadyCompleted() {
        JobParameters parameters = parametersOf("same-parameters", "2026-05-01", "202605");
        assertThat(batchLaunchGuard.launch(seedMasterJob, parameters).isLaunched()).isTrue();

        BatchLaunchResult second = batchLaunchGuard.launch(seedMasterJob, parameters);

        assertThat(second.status()).isEqualTo(BatchLaunchResult.Status.SKIPPED_ALREADY_COMPLETE);
    }

    @Test
    @DisplayName("이전 실행이 진행 중이면 다음 실행을 건너뛰고 상태를 남긴다")
    void skipsLaunchWhileAnotherExecutionIsRunning() throws Exception {
        JobParameters running = parametersOf("still-running", "2026-06-01", "202606");
        JobExecution inFlight = jobRepository.createJobExecution(SeedMasterJobConfig.SEED_MASTER_JOB, running);
        try {
            BatchLaunchResult result = batchLaunchGuard.launch(
                    seedMasterJob, parametersOf("still-running-other", "2026-06-02", "202606"));

            assertThat(result.status()).isEqualTo(BatchLaunchResult.Status.SKIPPED_RUNNING);
            assertThat(result.reason()).isNotBlank();
        } finally {
            inFlight.setStatus(BatchStatus.ABANDONED);
            inFlight.setEndTime(java.time.LocalDateTime.now());
            jobRepository.update(inFlight);
        }
    }

    private JobExecution launch(String seedVersion, String baseDate, String baseMonth) {
        BatchLaunchResult result =
                batchLaunchGuard.launch(seedMasterJob, parametersOf(seedVersion, baseDate, baseMonth));
        assertThat(result.isLaunched()).isTrue();
        return result.execution();
    }

    private JobParameters parametersOf(String seedVersion, String baseDate, String baseMonth) {
        return new JobParametersBuilder()
                .addString(SeedStepSpec.SEED_VERSION, seedVersion)
                .addString(SeedStepSpec.BASE_DATE, baseDate)
                .addString(SeedStepSpec.BASE_MONTH, baseMonth)
                .addLong("months", 1L, false)
                .toJobParameters();
    }

    private List<String> executedStepNames(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .map(StepExecution::getStepName)
                .toList();
    }

    private Map<String, String> skipReasons(JobExecution execution) {
        return execution.getExecutionContext().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(SeedStepGate.SKIP_REASON_PREFIX))
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().substring(SeedStepGate.SKIP_REASON_PREFIX.length()),
                        entry -> String.valueOf(entry.getValue())));
    }

    private int countOf(JdbcTemplate jdbcTemplate, String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
