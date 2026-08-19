package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.IntegrationTestSupport;
import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.infrastructure.external.LocalDataApiAdapter;
import SDD.smash.domain.infra.infrastructure.master.IndustryMaster;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore;
import SDD.smash.global.batch.launch.BatchLaunchGuard;
import SDD.smash.global.batch.launch.BatchLaunchResult;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 조립된 2-Step 흐름의 통합 검증. 조각(Plan/Reader/Listener/Store)은 단위로 검증돼 있으므로
 * 여기서는 <b>여러 날에 걸친 이어달리기</b>가 실제로 성립하는지만 본다.
 *
 * <pre>
 * 1일차  예산이 대상 하나뿐 → 절반만 수집 → infraStep 이 반영을 건너뛴다(infra 테이블 그대로)
 * 2일차  나머지 수집 → 회차 완성 판정 → infra 반영 → staging 정리
 * </pre>
 *
 * <p>외부 호출만 가짜로 바꾼다. 회차 계산·완성 판정·반영·정리는 전부 실제 빈이 한다.
 * 실제 MySQL 이 필요하다 — <b>Docker 데몬이 떠 있어야 한다.</b>
 */
class InfraCollectCheckpointIntegrationTest extends IntegrationTestSupport {

    private static final String SIDO_CODE = "48";
    private static final String JINJU = "48170";
    private static final String TONGYEONG = "48250";
    private static final LocalDataRegionCode JINJU_ORG = LocalDataRegionCode.of("3350000");
    private static final LocalDataRegionCode TONGYEONG_ORG = LocalDataRegionCode.of("3360000");
    private static final String INDUSTRY = "ITEST";

    /** 이 대상은 시설이 2개, 다른 대상은 1개 — 백분위가 갈려 반영 결과를 구분할 수 있다. */
    private static final Map<LocalDataRegionCode, Integer> FACILITY_COUNTS =
            Map.of(JINJU_ORG, 2, TONGYEONG_ORG, 1);

    @Autowired
    @Qualifier("infraJob")
    private Job infraJob;

    @Autowired
    private BatchLaunchGuard batchLaunchGuard;

    @Autowired
    private InfraCollectionStagingStore stagingStore;

    @Autowired
    @Qualifier("dataDBSource")
    private DataSource dataDataSource;

    /** 외부 LOCALDATA 호출만 가짜로 바꾼다. 수집 경로(API) 판정은 실제 어셈블러가 한다. */
    @MockitoBean
    private LocalDataApiAdapter localDataApiAdapter;

    /** 수집 대상 정의(지역 매핑 × 활성 업종)를 테스트용 2건으로 줄인다. */
    @MockitoBean
    private InfraMasterCatalog masterCatalog;

    private JdbcTemplate jdbcTemplate;

    /** 이번 실행에서 실제로 호출된 대상. 예산·중복 호출을 여기서 본다. */
    private final List<LocalDataRegionCode> requested = new ArrayList<>();

    /** 하루에 받을 수 있는 대상 수. 1일차에는 1이다. */
    private int dailyCapacity;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataDataSource);
        purgeStaging();
        deleteFixtures();
        insertFixtures();

        requested.clear();
        dailyCapacity = 1;

        given(masterCatalog.regionCodeMapping()).willReturn(new RegionCodeMapping(List.of(
                new RegionCodeMapping.Entry(JINJU_ORG, SigunguCode.of(JINJU), "진주시"),
                new RegionCodeMapping.Entry(TONGYEONG_ORG, SigunguCode.of(TONGYEONG), "통영시"))));
        given(masterCatalog.industryMaster()).willReturn(new IndustryMaster(List.of(
                new IndustryMasterEntry(IndustryCode.of(INDUSTRY), "테스트업종", Major.LIFE,
                        "test-slug", "dataset", true, true, null)), Map.of()));

        given(localDataApiAdapter.isReady()).willReturn(true);
        given(localDataApiAdapter.readinessDescription()).willReturn("fake");
        given(localDataApiAdapter.hasRemainingCapacity()).willAnswer(call -> requested.size() < dailyCapacity);
        given(localDataApiAdapter.collect(any(), any())).willAnswer(call -> {
            LocalDataRegionCode region = call.getArgument(1);
            requested.add(region);
            return facilities(region);
        });
    }

    @AfterEach
    void tearDown() {
        purgeStaging();
        deleteFixtures();
    }

    @Test
    @DisplayName("1일차 부분 수집은 반영하지 않고, 2일차에 회차가 완성되면 반영 후 staging 을 비운다")
    void appliesOnlyWhenRunIsCompleteAcrossDays() {
        // ---------- 1일차 — 예산이 대상 하나뿐이라 절반만 모인다
        JobExecution firstDay = launch("2026-08-19");

        assertThat(firstDay.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(requested).hasSize(1);
        assertThat(infraRows()).as("회차가 미완성이면 서비스 테이블을 건드리지 않는다").isEmpty();

        String runKey = stagingStore.runKeys().get(0);
        assertThat(stagingStore.completedTargets(runKey))
                .as("수집분은 staging 에 남아 다음 실행이 이어받는다").hasSize(1);

        // ---------- 2일차 — 예산이 회복되고 남은 대상만 받는다
        dailyCapacity = Integer.MAX_VALUE;
        JobExecution secondDay = launch("2026-08-20");

        assertThat(secondDay.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(requested).as("이미 수집한 대상은 다시 호출하지 않는다")
                .containsExactlyInAnyOrder(JINJU_ORG, TONGYEONG_ORG);

        // 회차가 완성됐으므로 반영된다 — 2지역 × 1업종
        assertThat(infraRows()).hasSize(2);
        assertThat(countOfInfra(JINJU)).isEqualTo(2);
        assertThat(countOfInfra(TONGYEONG)).isEqualTo(1);

        // 반영에 성공한 회차는 정리된다 — 다음 실행은 새 회차로 시작한다
        assertThat(stagingStore.completedTargets(runKey)).isEmpty();
        assertThat(stagingStore.counts(runKey)).isEmpty();
        assertThat(stagingStore.runKeys()).doesNotContain(runKey);
    }

    // ------------------------------------------------------------------ 헬퍼

    private JobExecution launch(String baseDate) {
        BatchLaunchResult result = batchLaunchGuard.launch(infraJob, new JobParametersBuilder()
                .addString("baseDate", baseDate)
                .toJobParameters());
        assertThat(result.isLaunched()).isTrue();
        return result.execution();
    }

    private FacilityCollection facilities(LocalDataRegionCode region) {
        List<InfraFacility> rows = new ArrayList<>();
        for (int i = 0; i < FACILITY_COUNTS.getOrDefault(region, 0); i++) {
            rows.add(new InfraFacility("MNG-" + region.value() + "-" + i, BusinessStatus.OPERATING, region));
        }
        return FacilityCollection.of(rows, 1);
    }

    private List<Map<String, Object>> infraRows() {
        return jdbcTemplate.queryForList(
                "SELECT sigungu_code, `count` FROM infra WHERE industry_code = ?", INDUSTRY);
    }

    private int countOfInfra(String sigunguCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT `count` FROM infra WHERE industry_code = ? AND sigungu_code = ?",
                Integer.class, INDUSTRY, sigunguCode);
        return count == null ? 0 : count;
    }

    private void purgeStaging() {
        stagingStore.runKeys().forEach(stagingStore::purge);
    }

    private void insertFixtures() {
        jdbcTemplate.update("INSERT INTO sido (sido_code, name) VALUES (?, ?)", SIDO_CODE, "경상남도");
        jdbcTemplate.update("INSERT INTO sigungu (sigungu_code, sido_code, name) VALUES (?, ?, ?)",
                JINJU, SIDO_CODE, "진주시");
        jdbcTemplate.update("INSERT INTO sigungu (sigungu_code, sido_code, name) VALUES (?, ?, ?)",
                TONGYEONG, SIDO_CODE, "통영시");
        jdbcTemplate.update("INSERT INTO industry (industry_code, name, major) VALUES (?, ?, ?)",
                INDUSTRY, "테스트업종", Major.LIFE.name());
    }

    /** 다른 통합 테스트가 행 수를 정확히 세므로 픽스처를 남기지 않는다. */
    private void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM infra WHERE industry_code = ?", INDUSTRY);
        jdbcTemplate.update("DELETE FROM industry WHERE industry_code = ?", INDUSTRY);
        jdbcTemplate.update("DELETE FROM sigungu WHERE sigungu_code IN (?, ?)", JINJU, TONGYEONG);
        jdbcTemplate.update("DELETE FROM sido WHERE sido_code = ?", SIDO_CODE);
    }
}
