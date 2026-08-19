package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.infra.domain.model.InfraScore;
import SDD.smash.domain.infra.domain.model.RegionIndustryCount;
import SDD.smash.domain.infra.domain.model.RegionIndustryStat;
import SDD.smash.domain.infra.domain.service.InfraStatPolicy;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraCollectTarget;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraSnapshot;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraTargetResult;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraUpsertRow;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaEntity;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaRepository;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore.TargetKey;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 인프라 적재 배치.
 *
 * <h2>왜 2-Step 인가 (운영 실측)</h2>
 * {@code API} 경로의 수집 대상은 229개 지역 × 16개 활성 업종 = 3,664개이고 대상당 평균 5.4회를
 * 호출해 전국 수집에 약 19,800회가 든다. 개발계정 실한도는 10,000회/일(예산 9,000회)이라
 * <b>하루에 끝낼 수 없다.</b> 2026-08 실측에서 5시간 17분을 수집하고 예산 초과로 전량을 폐기했다.
 *
 * <p>그래서 수집과 반영을 나눈다.
 * <pre>
 * infraCollectStep  아직 수집하지 않은 대상만 호출 → staging 에 <b>대상 하나 단위</b>로 커밋
 *                   예산이 소진되면 <b>COMPLETED</b> 로 끝난다(실패 아님). 다음 실행이 이어받는다
 * infraStep         staging 이 <b>완성됐을 때만</b> ratio/score 를 내고 infra 테이블에 반영
 *                   미완성이면 진척만 남기고 건너뛴다. 반영에 성공하면 그 회차 staging 을 정리한다
 * </pre>
 *
 * <p><b>백분위 모집단 불변식은 그대로다.</b> {@code InfraStatPolicy} 는 여전히 완전한 counts 만
 * 본다 — 부분 수집분은 staging 에만 머물고 서비스 테이블로 나가는 경로가 없다.
 *
 * <h2>경로가 갈린다</h2>
 * 체크포인트는 <b>{@code infra.collect.source=API} 전용</b>이다. {@code BULK_CSV} /
 * {@code LEGACY_CSV} 는 한 번에 전량을 받으므로 {@code infraCollectStep} 이 즉시 빈 스트림으로
 * 끝나고 {@code infraStep} 이 기존처럼 스냅샷을 통째로 조립해 반영한다.
 *
 * <h2>데이터 원천</h2>
 * 구 LOCALDATA 시드 CSV 가 아니라 <b>지방행정 인허가 데이터</b>에서 직접 수집한다
 * ({@link InfraSnapshotAssembler}).
 *
 * <p>Job 이름("infraJob")과 Step 빈 이름("infraStep")은 바꾸지 않는다 —
 * {@code SeedMasterJobConfig} 가 {@code @Qualifier} 로, {@code DataRefreshScheduler} 가
 * Job 이름으로 참조한다. 새 Step 은 "infraCollectStep" 으로 <b>추가</b>한다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class InfraBatchConfig {

    /** 회차 키의 기준 시간대. 예산도 한국 시간 자정에 리셋된다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 수집 청크 크기 = <b>1</b>. 대상 하나마다 "대상 진행 + 카운트"가 함께 커밋된다.
     *
     * <p><b>1보다 크게 잡으면 안 된다.</b> Spring Batch 는 {@code read()} 를 청크 트랜잭션
     * <b>안에서</b> 호출한다({@code TaskletStep} → {@code TransactionTemplate} →
     * {@code ChunkOrientedTasklet}). 이 Step 의 {@code read()} 는 외부 HTTP 호출이라
     * 청크를 20으로 잡으면 커밋 1회마다 약 108회(20 × 평균 5.4회)의 네트워크 왕복이
     * <b>열린 DB 트랜잭션 안에서</b> 일어나고 그동안 커넥션 하나를 붙잡는다
     * (persistence-conventions §6.3 위반). {@code wait_timeout} 이나 프록시가 유휴 커넥션을
     * 끊으면 청크 전체가 롤백돼 그만큼을 다시 받아야 한다.
     *
     * <p>체크포인트의 단위가 이미 "대상 하나"이고 원자성 요구도 대상 단위라 청크를 키울 이유가 없다.
     * 커밋은 3,664회로 늘지만 수집 자체가 5시간에 걸쳐 퍼지므로 부담이 되지 않는다.
     */
    private static final int COLLECT_CHUNK_SIZE = 1;

    private final JobRepository jobRepository;

    /**
     * 청크 트랜잭션 매니저. <b>이름을 명시</b>한다.
     *
     * <p>타입만으로 주입받으면 {@code @Primary} 해석에 결과가 걸린다. 지금은
     * {@code dataTransactionManager}(JPA)가 {@code @Primary} 라 우연히 맞지만, 이 설계의
     * 이중 합산 방지는 "staging 의 두 batchUpdate 가 <b>한</b> 트랜잭션에 묶인다"에 전적으로
     * 의존한다. 누군가 {@code @Primary} 를 {@code batchTransactionManager} 로 옮기는 순간
     * 두 쓰기가 별개의 autocommit 으로 쪼개져 "카운트만 커밋 → 재수집 시 이중 합산"이
     * 조용히 살아난다. 그래서 암묵 해석에 기대지 않고 못 박는다.
     */
    private final @Qualifier("dataTransactionManager") PlatformTransactionManager dataTransactionManager;
    private final IndustryJpaRepository industryJpaRepository;
    private final AddressQueryService addressQueryService;
    private final InfraSnapshotAssembler snapshotAssembler;
    private final InfraMasterCatalog masterCatalog;
    private final InfraCollectionStagingStore stagingStore;
    private final InfraScoreCacheCleaner infraScoreCacheCleaner;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    /**
     * 회차가 이 일수를 넘도록 완성되지 않으면 {@code log.error} 로 stall 을 알린다.
     *
     * <p>기본값 7일 — 예산 9,000회/일로 약 19,800회를 받으면 기대 소요는 2~3일이고, 재시도·부분
     * 실패·정기 실행 누락을 감안한 여유를 더한 값이다. 넘겼다고 <b>배치가 죽지는 않는다.</b>
     * 관측만 하고, 영구 실패 대상을 어떻게 할지는 사람이 정한다.
     */
    @Value("${infra.collect.stall-threshold-days:7}")
    private int stallThresholdDays;

    private Set<String> sigunguCodeCache = null;
    private Set<String> industryCodeCache = null;

    /**
     * 수집 → 반영. 한 Job 안에 두 Step 이 순서대로 있으므로 스케줄러/시드 마스터의 호출 방식은
     * 그대로다. 매일 이 Job 이 돌면 하루치씩 이어 모으고, 다 모인 날 자동으로 반영된다.
     *
     * <p><b>{@code .next()} 가 아니라 {@code .on("*")} 다.</b> 수집 Step 이 FAILED 로 끝나도
     * 반영 Step 은 돈다 — 반영은 "이 회차가 완성됐는가"만 보기 때문이다. 어제까지 회차를 다 채워
     * 두고 오늘 수집이 네트워크 장애로 죽었다는 이유로 <b>이미 완성된 회차의 반영이 막히면</b>
     * 그 회차는 다음 성공 실행까지 통째로 밀린다. 수집 실패와 반영은 별개의 사건이다.
     */
    @Bean
    public Job infraJob(@Qualifier("infraCollectStep") Step infraCollectStep,
                        @Qualifier("infraStep") Step infraStep) {
        return new JobBuilder("infraJob", jobRepository)
                // 원본이 바뀌었으므로 파생 점수 캐시를 버린다(redis-conventions §6.1).
                .listener(infraScoreCacheCleaner)
                .flow(infraCollectStep)
                .on("*").to(infraStep)
                .end()
                .build();
    }

    // ================================================================== STEP 1 — 수집

    /**
     * STEP 1 — 미수집 대상만 모아 staging 에 쌓는다. <b>서비스 테이블은 건드리지 않는다.</b>
     *
     * <p>예산 소진은 Reader 의 {@code null} 로 표현되므로 이 Step 은 COMPLETED 로 끝난다 —
     * "오늘 몫을 다 받았다"는 정상 종료이지 실패가 아니다. FAILED 로 끝나면 시드 마스터의
     * 집계에 실패로 잡히고 같은 {@code baseDate} 재실행 판정도 달라진다.
     */
    @Bean
    public Step infraCollectStep(InfraTargetCollectReader infraTargetCollectReader,
                                 ItemWriter<InfraTargetResult> infraStagingWriter) {
        return new StepBuilder("infraCollectStep", jobRepository)
                .<InfraTargetResult, InfraTargetResult>chunk(COLLECT_CHUNK_SIZE, dataTransactionManager)
                .reader(infraTargetCollectReader)
                .writer(infraStagingWriter)
                .listener((StepExecutionListener) new InfraCollectStepLogger(infraTargetCollectReader))
                .build();
    }

    /**
     * READER — 이어받을 회차를 정하고 <b>아직 수집하지 않은 대상만</b> 흘려보낸다.
     *
     * <p>API 경로가 아니거나 준비되지 않았으면 빈 목록을 넘겨 Step 이 즉시 끝나게 한다.
     * 빈 키로 외부 API 를 부르지 않고, 기존 적재분도 건드리지 않는다.
     */
    @Bean
    @StepScope
    public InfraTargetCollectReader infraTargetCollectReader(
            @Value("#{jobParameters['baseDate']}") String baseDate) {

        if (!usesCheckpoint()) {
            log.info("[infraJob] 체크포인트 수집은 API 경로 전용이다. source={} 는 infraStep 이 한 번에 처리한다.",
                    snapshotAssembler.source());
            return emptyCollectReader();
        }
        if (!snapshotAssembler.isReady()) {
            log.warn("[infraJob] 수집 건너뜀 baseDate={}, source={}, reason={}",
                    baseDate, snapshotAssembler.source(), snapshotAssembler.readinessDescription());
            return emptyCollectReader();
        }

        RegionCodeMapping mapping = masterCatalog.regionCodeMapping();
        List<IndustryMasterEntry> industries = masterCatalog.industryMaster().active();
        if (mapping.isEmpty() || industries.isEmpty()) {
            log.warn("[infraJob] 수집 대상이 없다. regions={}, industries={}", mapping.size(), industries.size());
            return emptyCollectReader();
        }

        LocalDate today = LocalDate.now(SEOUL);
        List<InfraCollectTarget> allTargets = InfraCollectPlan.allTargets(mapping, industries);
        String runKey = InfraCollectPlan.runKey(stagingStore.runKeys(), today);
        Set<TargetKey> completed = stagingStore.completedTargets(runKey);
        List<InfraCollectTarget> pending = InfraCollectPlan.pending(allTargets, completed);

        log.info("[infraJob] 수집 시작 baseDate={}, runKey={}, 진척={}/{}, 이번에 시도할 대상={}",
                baseDate, runKey, InfraCollectPlan.collectedCount(allTargets, completed),
                allTargets.size(), pending.size());

        return new InfraTargetCollectReader(runKey, snapshotAssembler.provider(), pending,
                mapping.asMap(), mapping.splitIndex(), today, stallThresholdDays);
    }

    /**
     * WRITER — 대상 진행 행과 그 대상이 만든 카운트 행을 <b>같은 트랜잭션</b>에 쓴다.
     *
     * <p>회차 키는 Reader 가 정한 것을 그대로 쓴다. 둘이 다른 회차를 가리키면 이어달리기가 깨진다.
     */
    @Bean
    @StepScope
    public ItemWriter<InfraTargetResult> infraStagingWriter(InfraTargetCollectReader infraTargetCollectReader) {
        return new InfraStagingWriter(stagingStore, infraTargetCollectReader.runKey());
    }

    // ================================================================== STEP 2 — 집계·반영

    @Bean
    public Step infraStep(ItemReader<RegionIndustryStat> infraSnapshotReader,
                          ItemProcessor<RegionIndustryStat, InfraUpsertRow> infraStatProcessor,
                          JdbcBatchItemWriter<InfraUpsertRow> infraWriter,
                          InfraStepLogger infraStepLogger) {
        return new StepBuilder("infraStep", jobRepository)
                .<RegionIndustryStat, InfraUpsertRow>chunk(500, dataTransactionManager)
                .reader(infraSnapshotReader)
                .processor(infraStatProcessor)
                .writer(infraWriter)
                .listener(infraStepLogger)
                .listener((StepExecutionListener) new InfraStagingCleanupListener(stagingStore))
                .build();
    }

    /**
     * READER — 반영할 스냅샷을 만든다. 두 경로가 여기서 갈린다.
     *
     * <ul>
     *   <li><b>API</b> — staging 이 완성됐을 때만 읽어 통계를 낸다. 미완성이면 진척을 남기고
     *       빈 스트림을 돌려준다(실패가 아니다). 완성이면 반영 후 정리 대상 회차를 표시한다.</li>
     *   <li><b>BULK_CSV / LEGACY_CSV</b> — 기존처럼 한 번에 전량을 조립한다.</li>
     * </ul>
     *
     * <p>어느 경로든 <b>완전한 counts</b> 만 {@code InfraStatPolicy} 에 넘어간다.
     *
     * @param baseDate 기준일 JobParameter. 같은 기준일 재실행 판정에 쓰이며 로그에 남는다
     */
    @Bean
    @StepScope
    public ItemReader<RegionIndustryStat> infraSnapshotReader(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            @Value("#{stepExecution}") StepExecution stepExecution) {

        // 정기 실행에서 업종/시군구 마스터가 갱신됐을 수 있으므로 참조 캐시를 매 Step 마다 새로 만든다.
        resetReferenceCaches();

        if (!snapshotAssembler.isReady()) {
            log.warn("[infraJob] 건너뜀 baseDate={}, source={}, reason={}",
                    baseDate, snapshotAssembler.source(), snapshotAssembler.readinessDescription());
            return new IteratorItemReader<>(List.<RegionIndustryStat>of());
        }
        if (usesCheckpoint()) {
            return stagingReader(baseDate, stepExecution);
        }
        return bulkReader(baseDate, stepExecution);
    }

    /** API 경로 — staging 이 완성됐을 때만 반영한다. */
    private ItemReader<RegionIndustryStat> stagingReader(String baseDate, StepExecution stepExecution) {

        RegionCodeMapping mapping = masterCatalog.regionCodeMapping();
        List<IndustryMasterEntry> industries = masterCatalog.industryMaster().active();
        if (mapping.isEmpty() || industries.isEmpty()) {
            log.warn("[infraJob] 반영 건너뜀 - 수집 대상 정의가 비었다. regions={}, industries={}",
                    mapping.size(), industries.size());
            return new IteratorItemReader<>(List.<RegionIndustryStat>of());
        }

        List<InfraCollectTarget> allTargets = InfraCollectPlan.allTargets(mapping, industries);
        String runKey = InfraCollectPlan.runKey(stagingStore.runKeys(), LocalDate.now(SEOUL));
        Set<TargetKey> completed = stagingStore.completedTargets(runKey);

        if (!InfraCollectPlan.isComplete(allTargets, completed)) {
            // 실패가 아니다. 며칠에 걸쳐 모으는 중이고 다음 실행이 이어받는다.
            log.info("[infraJob] 수집 진행 중 {}/{} (runKey={}) - 반영을 건너뛴다. 기존 스냅샷을 유지한다.",
                    InfraCollectPlan.collectedCount(allTargets, completed), allTargets.size(), runKey);
            return new IteratorItemReader<>(List.<RegionIndustryStat>of());
        }

        long startedAt = System.currentTimeMillis();
        List<RegionIndustryCount> counts = stagingStore.counts(runKey);
        List<RegionIndustryStat> rows = new InfraStatPolicy(snapshotAssembler.ratioBasis()).stats(counts);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        if (rows.isEmpty()) {
            // 완성됐는데 개수가 하나도 없다. 반영할 것이 없으니 회차만 정리하고 새로 시작한다.
            log.warn("[infraJob] 회차가 완성됐지만 집계 개수가 0이다 runKey={} - 정리 후 다음 회차를 시작한다.", runKey);
        }

        // 반영에 성공하면 이 회차를 지운다(정리는 InfraStagingCleanupListener 가 한다).
        stepExecution.getExecutionContext()
                .putString(InfraStagingCleanupListener.CTX_APPLIED_RUN_KEY, runKey);
        stepExecution.getExecutionContext().put(InfraStepLogger.CTX_SUMMARY, String.format(
                "source=API(staging), ratioBasis=%s, runKey=%s, targets=%d, countRows=%d, rows=%d, aggregateElapsed=%dms",
                snapshotAssembler.ratioBasis(), runKey, allTargets.size(), counts.size(), rows.size(), elapsedMs));

        log.info("[infraJob] 회차 완성 - 반영 시작 baseDate={}, runKey={}, targets={}, countRows={}, rows={}",
                baseDate, runKey, allTargets.size(), counts.size(), rows.size());

        return new IteratorItemReader<>(rows);
    }

    /** BULK_CSV / LEGACY_CSV — 한 번에 전량을 받는 경로. 기존 동작 그대로다. */
    private ItemReader<RegionIndustryStat> bulkReader(String baseDate, StepExecution stepExecution) {

        long startedAt = System.currentTimeMillis();
        InfraSnapshot snapshot = snapshotAssembler.assemble();
        long elapsedMs = System.currentTimeMillis() - startedAt;

        stepExecution.getExecutionContext().put(InfraStepLogger.CTX_SUMMARY, String.format(
                "source=%s, ratioBasis=%s, targets=%d, apiCalls=%d, read=%d, filteredOut=%d, "
                        + "duplicates=%d, unmappedRegions=%d, unmappedIndustries=%d, "
                        + "districtResolved=%d, districtUnresolved=%d, rows=%d, collectElapsed=%dms",
                snapshotAssembler.source(), snapshotAssembler.ratioBasis(), snapshot.targets(),
                snapshot.apiCalls(), snapshot.readCount(), snapshot.filteredOutCount(),
                snapshot.duplicateCount(), snapshot.unmappedRegions(), snapshot.unmappedIndustries(),
                snapshot.districtResolved(), snapshot.districtUnresolved(),
                snapshot.rows().size(), elapsedMs));

        log.info("[infraJob] 수집 완료 baseDate={}, source={}, targets={}, apiCalls={}, read={}, "
                        + "filteredOut={}, duplicates={}, unmappedRegions={}, unmappedIndustries={}, "
                        + "districtResolved={}, districtUnresolved={}, rows={}, elapsed={}ms",
                baseDate, snapshotAssembler.source(), snapshot.targets(), snapshot.apiCalls(),
                snapshot.readCount(), snapshot.filteredOutCount(), snapshot.duplicateCount(),
                snapshot.unmappedRegions(), snapshot.unmappedIndustries(),
                snapshot.districtResolved(), snapshot.districtUnresolved(),
                snapshot.rows().size(), elapsedMs);

        return new IteratorItemReader<>(snapshot.rows());
    }

    /**
     * PROCESSOR — 참조 무결성과 도메인 불변식을 확인한다. 어긋나면 {@code null}(skip)이다.
     *
     * <ul>
     *   <li>{@code sigungu} 마스터에 없는 코드 → skip (물리 FK 가 없으므로 여기서 막는다)</li>
     *   <li>{@code industry} 마스터에 없는 코드 → skip</li>
     *   <li>점수가 {@code [0, 100]} 밖 → skip. {@code InfraScore} 가 이미 강제하지만,
     *       외부에서 만들어진 값이 섞여 들어오는 경로를 대비해 한 번 더 확인한다</li>
     * </ul>
     */
    @Bean
    public ItemProcessor<RegionIndustryStat, InfraUpsertRow> infraStatProcessor() {
        return stat -> {
            String sigunguCode = stat.sigunguCode().value();
            String industryCode = stat.industryCode().value();

            if (!isKnownSigunguCode(sigunguCode)) {
                log.warn("[infraJob] 시군구 마스터에 없는 코드라 제외한다. sigungu={}", sigunguCode);
                return null;
            }
            if (!isKnownIndustryCode(industryCode)) {
                log.warn("[infraJob] 업종 마스터에 없는 코드라 제외한다. industry={}", industryCode);
                return null;
            }
            try {
                // 값 객체 재생성으로 불변식을 다시 확인한다(persistence-conventions §7.2).
                SigunguCode.of(sigunguCode);
                InfraScore.of(stat.score().value());
            } catch (DomainException e) {
                log.warn("[infraJob] 불변식 위반으로 제외한다. sigungu={}, industry={}, reason={}",
                        sigunguCode, industryCode, e.getMessage());
                return null;
            }

            return InfraUpsertRow.builder()
                    .sigunguCode(sigunguCode)
                    .industryCode(industryCode)
                    .count(stat.count())
                    .ratio(stat.ratio().value())
                    .score(stat.score().value())
                    .build();
        };
    }

    /**
     * WRITER — 같은 {@code (sigungu_code, industry_code)} 는 갱신한다.
     *
     * <p><b>{@code ratio} 와 {@code score} 도 UPDATE 절에 넣는다.</b> As-Is 는 {@code count} 만
     * 갱신해서 재적재해도 두 값이 최초 INSERT 값 그대로 남았다. 계산식을 바꿔도 반영되지 않는
     * 버그였다. 같은 기준일에 다시 돌려도 결과가 같으므로 멱등하다.
     */
    @Bean
    public JdbcBatchItemWriter<InfraUpsertRow> infraWriter() {
        String upsertSql = """
            INSERT INTO infra (sigungu_code, industry_code, `count`, ratio, score)
            VALUES (:sigunguCode, :industryCode, :count, :ratio, :score)
            ON DUPLICATE KEY UPDATE
                `count` = VALUES(`count`),
                ratio   = VALUES(ratio),
                score   = VALUES(score)
            """;

        return new JdbcBatchItemWriterBuilder<InfraUpsertRow>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }

    @Bean
    public InfraStepLogger infraStepLogger() {
        return new InfraStepLogger();
    }

    // ------------------------------------------------------------------ 보조

    /** 체크포인트(2-Step)를 쓰는 경로인가. 한 번에 전량을 받는 CSV 경로는 쓰지 않는다. */
    private boolean usesCheckpoint() {
        return snapshotAssembler.source() == InfraCollectSource.API;
    }

    private InfraTargetCollectReader emptyCollectReader() {
        LocalDate today = LocalDate.now(SEOUL);
        return new InfraTargetCollectReader(today.toString(), snapshotAssembler.provider(),
                List.of(), Map.of(), Map.of(), today, stallThresholdDays);
    }

    // ------------------------------------------------------------------ 참조 무결성 캐시

    private void resetReferenceCaches() {
        sigunguCodeCache = null;
        industryCodeCache = null;
    }

    private boolean isKnownSigunguCode(String sigunguCode) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = addressQueryService.getAllSigunguCodes()
                    .stream()
                    .map(SigunguCode::value)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(sigunguCode);
    }

    private boolean isKnownIndustryCode(String industryCode) {
        if (industryCodeCache == null) {
            industryCodeCache = industryJpaRepository.findAll()
                    .stream()
                    .map(IndustryJpaEntity::getCode)
                    .collect(Collectors.toSet());
        }
        return industryCodeCache.contains(industryCode);
    }
}
