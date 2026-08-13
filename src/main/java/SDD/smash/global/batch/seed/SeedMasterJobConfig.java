package SDD.smash.global.batch.seed;

import SDD.smash.global.batch.launch.BatchGuard;
import SDD.smash.global.batch.listener.SeedMasterJobListener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static SDD.smash.global.batch.seed.SeedDataPrerequisiteInspector.INDUSTRY;
import static SDD.smash.global.batch.seed.SeedDataPrerequisiteInspector.JOB_CODE_MIDDLE;
import static SDD.smash.global.batch.seed.SeedDataPrerequisiteInspector.JOB_CODE_TOP;
import static SDD.smash.global.batch.seed.SeedDataPrerequisiteInspector.SIDO;
import static SDD.smash.global.batch.seed.SeedDataPrerequisiteInspector.SIGUNGU;
import static SDD.smash.global.batch.seed.SeedStepSpec.BASE_DATE;
import static SDD.smash.global.batch.seed.SeedStepSpec.BASE_MONTH;
import static SDD.smash.global.batch.seed.SeedStepSpec.SEED_VERSION;

/**
 * 기동 시 도는 <b>단일</b> 시드 Job. 9개의 {@code ApplicationReadyEvent} 리스너와 {@code @Order} 를 대체한다.
 *
 * <h2>왜 {@code global/batch} 인가</h2>
 * FK 선후관계는 특정 컨텍스트의 관심사가 아니라 <b>애플리케이션 부트스트랩</b>의 관심사다.
 * 이 조립을 어느 한 컨텍스트(예: address)에 두면 그 컨텍스트의 {@code infrastructure} 가
 * 다른 컨텍스트의 {@code infrastructure} 를 알게 되어 architecture-conventions §4 표를 정면으로 어긴다.
 * {@code global} 은 컨텍스트가 아니라 공통 기반이고, {@code DataDBConfig} 가 컨텍스트별
 * persistence 패키지를 문자열로 열거하는 것과 같은 성격의 조립 지점이다(§7).
 *
 * <p>그래서 이 클래스는 <b>어떤 도메인 타입도 import 하지 않는다.</b> Step 은 Spring Batch 의
 * {@code Step} 타입으로, 빈 이름 문자열({@code @Qualifier})로만 참조한다.
 * 컨텍스트 경계를 넘는 컴파일 의존이 하나도 생기지 않는다.
 *
 * <h2>Flow</h2>
 * <pre>
 * [필수] SidoStep → SigunguStep → jcTopStep → jcMiddleStep
 *          ↳ 하나라도 FAILED 면 Job 을 FAILED 로 끝낸다(뒤 Step 은 돌지 않는다)
 * [외부] populationStep → industryStep → infraStep → jobCountStep → dwellingStep
 *          ↳ 각 Step 앞에 {@link SeedStepGate} 가 서서 조건을 못 채우면 건너뛰고 사유를 남긴다
 *          ↳ 실패해도 다음 Step 으로 넘어가고, Job 은 COMPLETED_WITH_EXTERNAL_FAILURES 로 끝난다
 * </pre>
 * 각 Step 빈은 개별 Job(SidoJob, dwellingJob …)에도 그대로 들어 있다. 개별 Job 은
 * 수동 실행·정기 실행용으로 남기고, <b>기동 시 자동 실행되는 경로는 seedMasterJob 하나뿐</b>이다.
 */
@Configuration
@Slf4j
public class SeedMasterJobConfig {

    public static final String SEED_MASTER_JOB = "seedMasterJob";

    private final JobRepository jobRepository;
    private final BatchGuard batchGuard;
    private final SeedDataPrerequisiteInspector prerequisiteInspector;

    // 적재 소스 설정. 값이 비어 있으면 해당 Step 을 건너뛴다(빈 경로로 파일을 열거나 빈 키로 API 를 부르지 않는다).
    @Value("${sido.filePath:}")           private String sidoFilePath;
    @Value("${sigungu.filePath:}")        private String sigunguFilePath;
    @Value("${jobCodeTop.filePath:}")     private String jobCodeTopFilePath;
    @Value("${jobCodeMiddle.filePath:}")  private String jobCodeMiddleFilePath;
    @Value("${apis.kosis.api-key:}")     private String kosisApiKey;
    @Value("${infra.industry-master.location:classpath:infra/industry-master.yml}")
    private String industryMasterLocation;
    @Value("${apis.datagokr.service-key:}") private String dataGoKrServiceKey;
    @Value("${apis.molit.service-key:}")  private String molitServiceKey;

    @Value("${seed.jobs.sido.enabled:true}")             private boolean sidoEnabled;
    @Value("${seed.jobs.sigungu.enabled:true}")          private boolean sigunguEnabled;
    @Value("${seed.jobs.job-code-top.enabled:true}")     private boolean jobCodeTopEnabled;
    @Value("${seed.jobs.job-code-middle.enabled:true}")  private boolean jobCodeMiddleEnabled;
    @Value("${seed.jobs.population.enabled:true}")       private boolean populationEnabled;
    @Value("${seed.jobs.industry.enabled:true}")         private boolean industryEnabled;
    @Value("${seed.jobs.infra.enabled:true}")            private boolean infraEnabled;
    @Value("${seed.jobs.job-count.enabled:true}")        private boolean jobCountEnabled;
    @Value("${seed.jobs.dwelling.enabled:true}")         private boolean dwellingEnabled;

    public SeedMasterJobConfig(JobRepository jobRepository, BatchGuard batchGuard,
                               SeedDataPrerequisiteInspector prerequisiteInspector) {
        this.jobRepository = jobRepository;
        this.batchGuard = batchGuard;
        this.prerequisiteInspector = prerequisiteInspector;
    }

    /**
     * Step 순서와 판정 규칙의 정본. 이 리스트의 순서가 곧 FK 적재 순서다.
     *
     * <p>빈으로 등록하지 않는다 — {@code List<SeedStepSpec>} 빈은 컬렉션 주입 규칙과 헷갈린다.
     */
    List<SeedStepSpec> seedStepSpecs() {
        List<SeedStepSpec> specs = new ArrayList<>();

        specs.add(new SeedStepSpec("SidoStep", SeedGroup.ESSENTIAL, sidoEnabled, SEED_VERSION,
                configs("sido.filePath", sidoFilePath), List.of(), SIDO));

        specs.add(new SeedStepSpec("SigunguStep", SeedGroup.ESSENTIAL, sigunguEnabled, SEED_VERSION,
                configs("sigungu.filePath", sigunguFilePath), List.of(SIDO), SIGUNGU));

        specs.add(new SeedStepSpec("jcTopStep", SeedGroup.ESSENTIAL, jobCodeTopEnabled, SEED_VERSION,
                configs("jobCodeTop.filePath", jobCodeTopFilePath), List.of(), JOB_CODE_TOP));

        specs.add(new SeedStepSpec("jcMiddleStep", SeedGroup.ESSENTIAL, jobCodeMiddleEnabled, SEED_VERSION,
                configs("jobCodeMiddle.filePath", jobCodeMiddleFilePath), List.of(JOB_CODE_TOP), JOB_CODE_MIDDLE));

        specs.add(new SeedStepSpec("populationStep", SeedGroup.EXTERNAL, populationEnabled, BASE_MONTH,
                configs("apis.kosis.api-key", kosisApiKey), List.of(SIGUNGU), null));

        specs.add(new SeedStepSpec("industryStep", SeedGroup.EXTERNAL, industryEnabled, BASE_DATE,
                configs("infra.industry-master.location", industryMasterLocation), List.of(), null));

        specs.add(new SeedStepSpec("infraStep", SeedGroup.EXTERNAL, infraEnabled, BASE_DATE,
                configs("apis.datagokr.service-key", dataGoKrServiceKey), List.of(SIGUNGU, INDUSTRY), null));

        specs.add(new SeedStepSpec("jobCountStep", SeedGroup.EXTERNAL, jobCountEnabled, BASE_DATE,
                configs("apis.datagokr.service-key", dataGoKrServiceKey), List.of(SIGUNGU, JOB_CODE_MIDDLE), null));

        specs.add(new SeedStepSpec("dwellingStep", SeedGroup.EXTERNAL, dwellingEnabled, BASE_MONTH,
                configs("apis.molit.service-key", molitServiceKey), List.of(SIGUNGU), null));

        return specs;
    }

    @Bean
    public SeedMasterJobListener seedMasterJobListener() {
        return new SeedMasterJobListener(seedStepSpecs(), jobRepository);
    }

    /**
     * @param jobExecutionListeners 파생 캐시 Cleaner 들. 타입으로만 받아
     *                              컨텍스트의 infrastructure 클래스를 import 하지 않는다.
     *                              seedMasterJob 이 원본을 갱신했으므로 파생 캐시를 버려야 한다
     *                              (redis-conventions §6.1).
     */
    @Bean
    public Job seedMasterJob(SeedMasterJobListener seedMasterJobListener,
                             List<JobExecutionListener> jobExecutionListeners,
                             @Qualifier("SidoStep") Step sidoStep,
                             @Qualifier("SigunguStep") Step sigunguStep,
                             @Qualifier("jcTopStep") Step jcTopStep,
                             @Qualifier("jcMiddleStep") Step jcMiddleStep,
                             @Qualifier("populationStep") Step populationStep,
                             @Qualifier("industryStep") Step industryStep,
                             @Qualifier("infraStep") Step infraStep,
                             @Qualifier("jobCountStep") Step jobCountStep,
                             @Qualifier("dwellingStep") Step dwellingStep) {

        Map<String, Step> stepsByName = new LinkedHashMap<>();
        stepsByName.put("SidoStep", sidoStep);
        stepsByName.put("SigunguStep", sigunguStep);
        stepsByName.put("jcTopStep", jcTopStep);
        stepsByName.put("jcMiddleStep", jcMiddleStep);
        stepsByName.put("populationStep", populationStep);
        stepsByName.put("industryStep", industryStep);
        stepsByName.put("infraStep", infraStep);
        stepsByName.put("jobCountStep", jobCountStep);
        stepsByName.put("dwellingStep", dwellingStep);

        JobBuilder builder = new JobBuilder(SEED_MASTER_JOB, jobRepository);
        builder.listener(seedMasterJobListener);
        jobExecutionListeners.stream()
                .filter(listener -> !(listener instanceof SeedMasterJobListener))
                .forEach(builder::listener);

        return builder.start(seedMasterFlow(seedStepSpecs(), stepsByName)).end().build();
    }

    /**
     * 관문 → Step 을 9번 잇는다.
     * <ul>
     *   <li>{@code 관문 --RUN--> Step}, {@code 관문 --SKIP--> 다음 관문}</li>
     *   <li>필수 Step 은 {@code --FAILED--> fail()} 로 Job 을 끝낸다</li>
     *   <li>외부 Step 은 {@code --*--> 다음 관문} 이라 실패해도 흐름이 멈추지 않는다</li>
     * </ul>
     */
    private Flow seedMasterFlow(List<SeedStepSpec> specs, Map<String, Step> stepsByName) {

        List<SeedStepGate> gates = specs.stream()
                .map(spec -> new SeedStepGate(spec, batchGuard, prerequisiteInspector))
                .toList();

        FlowBuilder<Flow> flowBuilder = new FlowBuilder<>("seedMasterFlow");
        flowBuilder.start(gates.get(0));

        for (int i = 0; i < specs.size(); i++) {
            SeedStepSpec spec = specs.get(i);
            SeedStepGate gate = gates.get(i);
            Step step = stepsByName.get(spec.stepName());
            boolean last = (i == specs.size() - 1);

            if (last) {
                flowBuilder.from(gate).on(SeedStepGate.SKIP.getName()).end();
            } else {
                flowBuilder.from(gate).on(SeedStepGate.SKIP.getName()).to(gates.get(i + 1));
            }
            flowBuilder.from(gate).on(SeedStepGate.RUN.getName()).to(step);

            if (spec.group().isEssential()) {
                flowBuilder.from(step).on("FAILED").fail();
            }
            if (last) {
                flowBuilder.from(step).on("*").end();
            } else {
                flowBuilder.from(step).on("*").to(gates.get(i + 1));
            }
        }

        return flowBuilder.build();
    }

    private Map<String, String> configs(String key, String value) {
        Map<String, String> configs = new LinkedHashMap<>();
        configs.put(key, value);
        return configs;
    }
}
