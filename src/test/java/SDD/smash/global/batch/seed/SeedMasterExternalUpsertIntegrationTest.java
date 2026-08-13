package SDD.smash.global.batch.seed;

import SDD.smash.global.batch.launch.BatchLaunchGuard;
import SDD.smash.global.batch.launch.BatchLaunchResult;

import SDD.smash.IntegrationTestSupport;
import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.domain.port.PopulationSnapshotProvider;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.domain.dwelling.domain.port.RentRecordProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import SDD.smash.global.domain.model.SigunguCode;

import javax.sql.DataSource;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 외부 갱신 Step 의 Upsert 가 <b>리눅스 MySQL</b>(Testcontainers)에서 실제로 동작하는지 검증한다.
 *
 * <p>이 테스트가 존재하는 이유는 테이블명 대소문자다. {@code hbm2ddl.auto=update} 가 만드는 테이블은
 * {@code population} / {@code dwelling}(소문자)인데 Upsert SQL 은 {@code Population} / {@code Dwelling}
 * 이었다. 개발자 PC(Windows/macOS)의 MySQL 은 {@code lower_case_table_names=1} 이라 통과하지만
 * 리눅스 컨테이너에서는 "table doesn't exist" 로 실패한다. 여기서 그 경로를 실제로 지난다.
 *
 * <p>국토부 API({@link RentRecordProvider})와 KOSIS API({@link PopulationSnapshotProvider})는
 * 포트를 대역으로 바꿔 호출하지 않는다.
 */
@TestPropertySource(properties = {
        "seed.master.enabled=false",
        "sido.filePath=src/test/resources/seed/sido.csv",
        "sigungu.filePath=src/test/resources/seed/sigungu.csv",
        "jobCodeTop.filePath=src/test/resources/seed/level_top.csv",
        "jobCodeMiddle.filePath=src/test/resources/seed/level_middle.csv",
        // 인구는 CSV 가 아니라 KOSIS API 다. 게이트를 열기 위한 더미 키이고, 포트는 아래에서 대역으로 바꾼다.
        "apis.kosis.api-key=test-key"
})
class SeedMasterExternalUpsertIntegrationTest extends IntegrationTestSupport {

    @Autowired
    @Qualifier(SeedMasterJobConfig.SEED_MASTER_JOB)
    private Job seedMasterJob;

    @Autowired
    private BatchLaunchGuard batchLaunchGuard;

    @Autowired
    @Qualifier("dataDBSource")
    private DataSource dataDataSource;

    @MockitoBean
    private RentRecordProvider rentRecordProvider;

    @MockitoBean
    private PopulationSnapshotProvider populationSnapshotProvider;

    @Test
    @DisplayName("인구·전월세 Upsert 가 소문자 테이블에 적재되고 기준월이 바뀌어도 중복 행이 생기지 않는다")
    void upsertsExternalDataWithoutDuplicatingRows() {
        given(populationSnapshotProvider.isAvailable()).willReturn(true);
        given(populationSnapshotProvider.fetchLatestNotAfter(any())).willAnswer(invocation -> {
            YearMonth month = invocation.getArgument(0);
            return List.of(
                    PopulationSnapshot.of(SigunguCode.of("11110"), 141_000, month),
                    PopulationSnapshot.of(SigunguCode.of("11140"), 120_000, month),
                    PopulationSnapshot.of(SigunguCode.of("26110"), 44_000, month));
        });
        given(populationSnapshotProvider.fetch(any())).willAnswer(invocation ->
                populationSnapshotProvider.fetchLatestNotAfter(invocation.getArgument(0)));

        given(rentRecordProvider.fetch(any(), any())).willReturn(List.of(
                new RentRecord("테스트아파트", "1-1", 20_000, 0),
                new RentRecord("테스트아파트", "1-2", 5_000, 70)));

        JobExecution first = launch("upsert-v1", "2026-07-01", "202607");

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(executedStepNames(first)).contains("populationStep", "dwellingStep");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataDataSource);
        assertThat(countOf(jdbcTemplate, "population")).isEqualTo(3);
        assertThat(countOf(jdbcTemplate, "dwelling")).isEqualTo(3);

        // 기준월이 바뀌면 다시 돌지만 같은 시군구는 갱신되기만 해야 한다
        JobExecution second = launch("upsert-v1", "2026-08-01", "202608");

        assertThat(executedStepNames(second)).contains("populationStep", "dwellingStep");
        assertThat(countOf(jdbcTemplate, "population")).isEqualTo(3);
        assertThat(countOf(jdbcTemplate, "dwelling")).isEqualTo(3);
    }

    private JobExecution launch(String seedVersion, String baseDate, String baseMonth) {
        BatchLaunchResult result = batchLaunchGuard.launch(seedMasterJob, new JobParametersBuilder()
                .addString(SeedStepSpec.SEED_VERSION, seedVersion)
                .addString(SeedStepSpec.BASE_DATE, baseDate)
                .addString(SeedStepSpec.BASE_MONTH, baseMonth)
                .addLong("months", 1L, false)
                .toJobParameters());
        assertThat(result.isLaunched()).isTrue();
        return result.execution();
    }

    private List<String> executedStepNames(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .map(StepExecution::getStepName)
                .toList();
    }

    private int countOf(JdbcTemplate jdbcTemplate, String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
