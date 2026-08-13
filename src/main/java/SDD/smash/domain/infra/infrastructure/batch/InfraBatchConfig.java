package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.infra.domain.model.InfraScore;
import SDD.smash.domain.infra.domain.model.RegionIndustryStat;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraSnapshot;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraUpsertRow;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaEntity;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaRepository;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 인프라 적재 배치.
 *
 * <h2>데이터 원천</h2>
 * 구 LOCALDATA 시드 CSV 가 아니라 <b>지방행정 인허가 데이터</b>에서 직접 수집한다
 * ({@link InfraSnapshotAssembler}). 경로는 {@code infra.collect.source} 가 정하고
 * 기본은 공식 API 다. 레거시 CSV 는 명시적 옵션({@code LEGACY_CSV})으로만 남는다.
 *
 * <h2>왜 Reader 가 스냅샷을 통째로 만드는가</h2>
 * {@code ratio}(시군구 내 구성비)와 {@code score}(업종별 전국 백분위)는 스냅샷 전체를 봐야
 * 계산된다. 그리고 수집이 도중에 실패하면 <b>한 행도 쓰지 않아야</b> 기존 정상 스냅샷이 남는다.
 * Reader 가 조립 단계에서 예외를 던지면 청크가 한 번도 돌지 않으므로 이 성질이 자연히 성립한다.
 *
 * <h2>고친 것</h2>
 * <ul>
 *   <li>Upsert 의 {@code ON DUPLICATE KEY UPDATE} 가 {@code count} 만 갱신해
 *       재적재해도 {@code ratio}/{@code score} 가 최초 INSERT 값에 고정되던 버그 → 셋 다 갱신한다.</li>
 *   <li>{@code InfraScoreCacheCleaner} 가 어느 Job 에도 연결돼 있지 않아 인프라 갱신이
 *       최대 24시간(캐시 TTL) 반영되지 않던 버그 → {@code infraJob} 에 리스너로 연결한다.</li>
 *   <li>{@code InfraUpsertRow.count} 가 {@code String} 이던 것 → {@code Integer}.</li>
 *   <li>Processor 에 점수 범위 검증이 없어 100 초과 값이 적재되고 추천 API 호출 시점에
 *       HTTP 400 으로 터지던 문제 → {@code InfraScore} 값 객체가 적재 전에 막는다.</li>
 * </ul>
 *
 * <p>Job 이름("infraJob")과 Step 빈 이름("infraStep")은 바꾸지 않는다 —
 * {@code SeedMasterJobConfig} 가 {@code @Qualifier} 로, {@code DataRefreshScheduler} 가
 * Job 이름으로 참조한다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class InfraBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final IndustryJpaRepository industryJpaRepository;
    private final AddressQueryService addressQueryService;
    private final InfraSnapshotAssembler snapshotAssembler;
    private final InfraScoreCacheCleaner infraScoreCacheCleaner;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    private Set<String> sigunguCodeCache = null;
    private Set<String> industryCodeCache = null;

    @Bean
    public Job infraJob(Step infraStep) {
        return new JobBuilder("infraJob", jobRepository)
                // 원본이 바뀌었으므로 파생 점수 캐시를 버린다(redis-conventions §6.1).
                .listener(infraScoreCacheCleaner)
                .start(infraStep)
                .build();
    }

    @Bean
    public Step infraStep(ItemReader<RegionIndustryStat> infraSnapshotReader,
                          ItemProcessor<RegionIndustryStat, InfraUpsertRow> infraStatProcessor,
                          JdbcBatchItemWriter<InfraUpsertRow> infraWriter,
                          InfraStepLogger infraStepLogger) {
        return new StepBuilder("infraStep", jobRepository)
                .<RegionIndustryStat, InfraUpsertRow>chunk(500, platformTransactionManager)
                .reader(infraSnapshotReader)
                .processor(infraStatProcessor)
                .writer(infraWriter)
                .listener(infraStepLogger)
                .build();
    }

    /**
     * READER — 스냅샷을 통째로 만들어 흘려보낸다.
     *
     * <p>수집 경로가 준비되지 않았으면(예: 인증키 없음) <b>빈 Reader 를 돌려 배치를 건너뛴다.</b>
     * 빈 키로 외부 API 를 부르지 않고, 기존 적재분도 건드리지 않는다.
     *
     * @param baseDate 기준일 JobParameter. 같은 기준일 재실행 판정에 쓰이며 로그에 남는다
     */
    @Bean
    @StepScope
    public ItemReader<RegionIndustryStat> infraSnapshotReader(
            @Value("#{jobParameters['baseDate']}") String baseDate,
            @Value("#{stepExecution}") org.springframework.batch.core.StepExecution stepExecution) {

        // 정기 실행에서 업종/시군구 마스터가 갱신됐을 수 있으므로 참조 캐시를 매 Step 마다 새로 만든다.
        resetReferenceCaches();

        if (!snapshotAssembler.isReady()) {
            log.warn("[infraJob] 건너뜀 baseDate={}, source={}, reason={}",
                    baseDate, snapshotAssembler.source(), snapshotAssembler.readinessDescription());
            return new IteratorItemReader<>(List.<RegionIndustryStat>of());
        }

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
