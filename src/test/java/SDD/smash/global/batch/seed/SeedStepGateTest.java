package SDD.smash.global.batch.seed;

import SDD.smash.global.batch.launch.BatchGuard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SeedStepGateTest {

    private static final String SECRET_KEY_VALUE = "super-secret-service-key";

    @Mock
    BatchGuard batchGuard;

    @Mock
    SeedDataPrerequisiteInspector prerequisiteInspector;

    @Test
    @DisplayName("비활성화된 Step 은 건너뛰고 사유를 남긴다")
    void skipsDisabledStep() {
        SeedStepGate gate = gateOf(specBuilder().enabled(false).build());

        JobExecution jobExecution = jobExecution();
        FlowExecutionStatus decision = gate.decide(jobExecution, null);

        assertThat(decision).isEqualTo(SeedStepGate.SKIP);
        assertThat(skipReason(jobExecution, "populationStep")).contains("비활성화");
        then(batchGuard).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("필수 설정이 비어 있으면 건너뛰고 설정 값이 아니라 키 이름만 사유에 남긴다")
    void skipsWhenRequiredConfigIsBlankWithoutLeakingTheValue() {
        Map<String, String> configs = new java.util.LinkedHashMap<>();
        configs.put("apis.molit.service-key", "");
        configs.put("apis.molit.base-url", SECRET_KEY_VALUE);   // 값이 채워진 설정은 사유에 나오지 않아야 한다

        SeedStepGate gate = gateOf(specBuilder().requiredConfigs(configs).build());

        JobExecution jobExecution = jobExecution();
        FlowExecutionStatus decision = gate.decide(jobExecution, null);

        assertThat(decision).isEqualTo(SeedStepGate.SKIP);
        assertThat(skipReason(jobExecution, "populationStep"))
                .contains("apis.molit.service-key")
                .doesNotContain(SECRET_KEY_VALUE);
    }

    @Test
    @DisplayName("기준 파라미터가 없으면 건너뛴다")
    void skipsWhenGuardParameterIsAbsent() {
        SeedStepGate gate = gateOf(specBuilder().build());

        JobExecution jobExecution = new JobExecution(1L, new JobParameters());
        FlowExecutionStatus decision = gate.decide(jobExecution, null);

        assertThat(decision).isEqualTo(SeedStepGate.SKIP);
        assertThat(skipReason(jobExecution, "populationStep")).contains("baseMonth");
    }

    @Test
    @DisplayName("선행 기준 데이터가 비어 있으면 실행하지 않고 사유를 남긴다")
    void skipsWhenPrerequisiteTableIsEmpty() {
        given(prerequisiteInspector.hasRows("sigungu")).willReturn(false);
        SeedStepGate gate = gateOf(specBuilder().requiredTables(List.of("sigungu")).build());

        JobExecution jobExecution = jobExecution();
        FlowExecutionStatus decision = gate.decide(jobExecution, null);

        assertThat(decision).isEqualTo(SeedStepGate.SKIP);
        assertThat(skipReason(jobExecution, "populationStep")).contains("선행 기준 데이터 없음", "sigungu");
        then(batchGuard).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("같은 기준월로 이미 완료됐으면 건너뛴다")
    void skipsWhenAlreadyCompletedForSameBaseMonth() {
        given(batchGuard.stepAlreadyCompleted("populationStep", "baseMonth", "202608")).willReturn(true);
        SeedStepGate gate = gateOf(specBuilder().build());

        JobExecution jobExecution = jobExecution();
        FlowExecutionStatus decision = gate.decide(jobExecution, null);

        assertThat(decision).isEqualTo(SeedStepGate.SKIP);
        assertThat(skipReason(jobExecution, "populationStep")).contains("이미 완료됨", "202608");
    }

    @Test
    @DisplayName("이력은 있어도 적재 테이블이 비었으면 다시 실행한다")
    void runsAgainWhenHistoryExistsButTargetTableIsEmpty() {
        given(batchGuard.stepAlreadyCompleted("SidoStep", "seedVersion", "v1")).willReturn(true);
        given(prerequisiteInspector.hasRows("sido")).willReturn(false);

        SeedStepGate gate = gateOf(new SeedStepSpec("SidoStep", SeedGroup.ESSENTIAL, true,
                SeedStepSpec.SEED_VERSION, Map.of("sido.filePath", "data/static/sido.csv"),
                List.of(), "sido"));

        FlowExecutionStatus decision = gate.decide(jobExecution(), null);

        assertThat(decision).isEqualTo(SeedStepGate.RUN);
    }

    @Test
    @DisplayName("조건을 모두 만족하면 실행한다")
    void runsWhenEveryConditionIsSatisfied() {
        given(batchGuard.stepAlreadyCompleted("populationStep", "baseMonth", "202608")).willReturn(false);
        given(prerequisiteInspector.hasRows("sigungu")).willReturn(true);

        SeedStepGate gate = gateOf(specBuilder().requiredTables(List.of("sigungu")).build());

        assertThat(gate.decide(jobExecution(), null)).isEqualTo(SeedStepGate.RUN);
    }

    private SeedStepGate gateOf(SeedStepSpec spec) {
        return new SeedStepGate(spec, batchGuard, prerequisiteInspector);
    }

    private JobExecution jobExecution() {
        JobParameters parameters = new JobParametersBuilder()
                .addString(SeedStepSpec.SEED_VERSION, "v1")
                .addString(SeedStepSpec.BASE_DATE, "2026-08-13")
                .addString(SeedStepSpec.BASE_MONTH, "202608")
                .toJobParameters();
        return new JobExecution(1L, parameters);
    }

    private String skipReason(JobExecution jobExecution, String stepName) {
        return jobExecution.getExecutionContext().getString(SeedStepGate.SKIP_REASON_PREFIX + stepName);
    }

    /** 인구 Step 기본형 — 필요한 조건만 바꿔 쓴다. */
    private SpecBuilder specBuilder() {
        return new SpecBuilder();
    }

    private static final class SpecBuilder {
        private boolean enabled = true;
        private Map<String, String> requiredConfigs = Map.of("population.filePath", "data/generated/population.csv");
        private List<String> requiredTables = List.of();

        SpecBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        SpecBuilder requiredConfigs(Map<String, String> requiredConfigs) {
            this.requiredConfigs = requiredConfigs;
            return this;
        }

        SpecBuilder requiredTables(List<String> requiredTables) {
            this.requiredTables = requiredTables;
            return this;
        }

        SeedStepSpec build() {
            return new SeedStepSpec("populationStep", SeedGroup.EXTERNAL, enabled,
                    SeedStepSpec.BASE_MONTH, requiredConfigs, requiredTables, null);
        }
    }
}
