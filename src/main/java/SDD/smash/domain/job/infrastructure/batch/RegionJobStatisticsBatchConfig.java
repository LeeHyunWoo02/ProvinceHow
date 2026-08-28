package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.infrastructure.batch.dto.RegionJobStatisticsCsvRow;
import SDD.smash.domain.job.infrastructure.batch.dto.RegionJobStatisticsUpsertRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaEntity;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaRepository;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.File;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static SDD.smash.global.util.BatchTextUtil.addLeadingZero;
import static SDD.smash.global.util.BatchTextUtil.digitsOnly;
import static SDD.smash.global.util.BatchTextUtil.isBlank;
import static SDD.smash.global.util.BatchTextUtil.normalize;

/**
 * EIS 고용행정통계 시드 배치. {@code data/static/eis_job_stats.csv} → {@code region_job_statistics}.
 *
 * <p>{@code JobCountBatchConfig} 를 본떴다. 다른 점은 두 가지다.
 * <ul>
 *   <li>FK 대상이 직종 <b>대분류</b>({@code job_code_top}) 다. 중분류가 아니다.</li>
 *   <li>파일이 없거나 비어도 <b>실패시키지 않는다</b>. 경로 프로퍼티가 빈 값이라 배포가 통째로
 *       깨진 전례가 있어, 이 Step 은 경고만 남기고 0건으로 끝낸다.</li>
 * </ul>
 *
 * <p>파생 캐시 무효화 대상이 없다 — 점수({@code JobScorePolicy})의 입력은 아직 {@code JobCount} 다.
 */
@Configuration
@Slf4j
public class RegionJobStatisticsBatchConfig {

    /**
     * 119,700행이다. Reader 가 한 줄씩 흘려보내므로 메모리는 청크 크기로만 결정된다.
     * 1,000행이면 Upsert 왕복이 120회로 끝나고 청크 트랜잭션도 길지 않다.
     */
    private static final int CHUNK_SIZE = 1000;

    /** {@code sigungu_code,sido_code,sigungu_name,job_top_code,year_month,지표 5} */
    private static final int COLUMN_COUNT = 10;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final AddressQueryService addressQueryService;
    private final JobCodeTopJpaRepository jobCodeTopJpaRepository;
    private final DataSource dataDataSource;

    /**
     * 명시적 생성자다. 이 프로젝트에는 {@code lombok.config} 가 없어 {@code @RequiredArgsConstructor}
     * 가 만드는 생성자에 필드의 {@code @Qualifier} 가 복사되지 않는다. 파라미터에 붙여 못 박는다.
     */
    public RegionJobStatisticsBatchConfig(
            JobRepository jobRepository,
            @Qualifier("dataTransactionManager") PlatformTransactionManager platformTransactionManager,
            AddressQueryService addressQueryService,
            JobCodeTopJpaRepository jobCodeTopJpaRepository,
            @Qualifier("dataDBSource") DataSource dataDataSource) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.addressQueryService = addressQueryService;
        this.jobCodeTopJpaRepository = jobCodeTopJpaRepository;
        this.dataDataSource = dataDataSource;
    }

    @Value("${regionJobStatistics.filePath:}")
    private String filePath;

    private Set<String> sigunguCodeCache = null;
    private Set<String> jobTopCodeCache = null;

    private final AtomicLong unknownSigunguCount = new AtomicLong();
    private final AtomicLong unknownJobCodeCount = new AtomicLong();
    private final AtomicLong invalidValueCount = new AtomicLong();

    @Bean
    public Job regionJobStatisticsJob() {
        return new JobBuilder("regionJobStatisticsJob", jobRepository)
                .start(regionJobStatisticsStep())
                .build();
    }

    @Bean
    public Step regionJobStatisticsStep() {
        return new StepBuilder("regionJobStatisticsStep", jobRepository)
                .<RegionJobStatisticsCsvRow, RegionJobStatisticsUpsertRow> chunk(CHUNK_SIZE, platformTransactionManager)
                .reader(regionJobStatisticsCsvReader())
                .processor(regionJobStatisticsProcessor())
                .writer(regionJobStatisticsWriter())
                .listener(regionJobStatisticsStepListener())
                .build();
    }

    /**
     * 파일 유무 경고와 skip 집계를 담당한다. 건너뛴 건수를 남기지 않으면
     * "성공했는데 데이터가 적다"를 사후에 설명할 수 없다.
     */
    private StepExecutionListener regionJobStatisticsStepListener() {
        return new StepExecutionListener() {

            @Override
            public void beforeStep(StepExecution stepExecution) {
                unknownSigunguCount.set(0);
                unknownJobCodeCount.set(0);
                invalidValueCount.set(0);
                sigunguCodeCache = null;
                jobTopCodeCache = null;

                File file = new File(filePath == null ? "" : filePath);
                if (isBlank(filePath) || !file.isFile()) {
                    log.warn("[regionJobStatisticsStep] 시드 파일이 없어 0건으로 끝낸다. path={}", filePath);
                } else if (file.length() == 0L) {
                    log.warn("[regionJobStatisticsStep] 시드 파일이 비어 있다. path={}", filePath);
                }
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                log.info("[regionJobStatisticsStep] 적재={}건, skip: 미등록시군구={} 미등록직종={} 값오류={}",
                        stepExecution.getWriteCount(),
                        unknownSigunguCount.get(), unknownJobCodeCount.get(), invalidValueCount.get());
                return stepExecution.getExitStatus();
            }
        };
    }

    /**
     * UTF-8 CSV. {@code strict(false)} 라 파일이 없으면 예외 대신 0건으로 끝난다
     * (경고는 Step 리스너가 남긴다).
     */
    @Bean
    @StepScope
    public FlatFileItemReader<RegionJobStatisticsCsvRow> regionJobStatisticsCsvReader() {

        return new FlatFileItemReaderBuilder<RegionJobStatisticsCsvRow>()
                .name("regionJobStatisticsCsvReader")
                .resource(new FileSystemResource(filePath == null ? "" : filePath))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(false)
                .delimited()
                .delimiter(",")
                .names("sigungu_code", "sido_code", "sigungu_name", "job_top_code", "year_month",
                        "job_openings", "job_seekers", "placements", "valid_openings", "valid_seekers")
                .fieldSetMapper(fieldSet -> {
                    if (fieldSet.getFieldCount() != COLUMN_COUNT) {
                        throw new IllegalArgumentException(
                                "고용행정통계 CSV 는 %d 개 열이어야 한다. 실제=%d"
                                        .formatted(COLUMN_COUNT, fieldSet.getFieldCount()));
                    }
                    return new RegionJobStatisticsCsvRow(
                            normalize(fieldSet.readString(0)),
                            normalize(fieldSet.readString(3)),
                            normalize(fieldSet.readString(4)),
                            parseLongOrNull(fieldSet.readString(5)),
                            parseLongOrNull(fieldSet.readString(6)),
                            parseLongOrNull(fieldSet.readString(7)),
                            parseLongOrNull(fieldSet.readString(8)),
                            parseLongOrNull(fieldSet.readString(9)));
                })
                .build();
    }

    /**
     * 존재하지 않는 시군구·직종 대분류와 값이 깨진 행을 건너뛴다(반환 {@code null}).
     * 불변식 검증은 도메인 Aggregate 생성으로 대신한다 — 기준월 형식과 음수가 여기서 걸린다.
     */
    @Bean
    public ItemProcessor<RegionJobStatisticsCsvRow, RegionJobStatisticsUpsertRow> regionJobStatisticsProcessor() {
        return row -> {
            String sigunguCode = row.sigunguCode();
            String jobTopCode = addLeadingZero(row.jobTopCode());

            if (isBlank(sigunguCode) || !isKnownSigunguCode(sigunguCode)) {
                unknownSigunguCount.incrementAndGet();
                return null;
            }
            if (isBlank(jobTopCode) || !isKnownJobTopCode(jobTopCode)) {
                unknownJobCodeCount.incrementAndGet();
                return null;
            }
            if (hasMissingMeasure(row)) {
                invalidValueCount.incrementAndGet();
                return null;
            }

            try {
                RegionJobStatistics statistics = RegionJobStatistics.of(
                        new RegionJobStatisticsKey(
                                SigunguCode.of(sigunguCode),
                                JobCode.of(jobTopCode),
                                StatisticsMonth.of(row.yearMonth())),
                        row.jobOpenings(), row.jobSeekers(), row.placements(),
                        row.validOpenings(), row.validSeekers());

                return RegionJobStatisticsUpsertRow.builder()
                        .sigunguCode(statistics.sigunguCode().value())
                        .jobTopCode(statistics.jobCode().value())
                        .statMonth(statistics.month().text())
                        .jobOpenings(statistics.jobOpenings())
                        .jobSeekers(statistics.jobSeekers())
                        .placements(statistics.placements())
                        .validOpenings(statistics.validOpenings())
                        .validSeekers(statistics.validSeekers())
                        .build();
            } catch (DomainException e) {
                invalidValueCount.incrementAndGet();
                return null;
            }
        };
    }

    @Bean
    public JdbcBatchItemWriter<RegionJobStatisticsUpsertRow> regionJobStatisticsWriter() {

        String upsertSql = """
            INSERT INTO region_job_statistics
                (sigungu_code, job_top_code, stat_month,
                 job_openings, job_seekers, placements, valid_openings, valid_seekers)
            VALUES (:sigunguCode, :jobTopCode, :statMonth,
                 :jobOpenings, :jobSeekers, :placements, :validOpenings, :validSeekers)
            ON DUPLICATE KEY UPDATE
                job_openings = VALUES(job_openings),
                job_seekers = VALUES(job_seekers),
                placements = VALUES(placements),
                valid_openings = VALUES(valid_openings),
                valid_seekers = VALUES(valid_seekers)
            """;

        return new JdbcBatchItemWriterBuilder<RegionJobStatisticsUpsertRow>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }

    private boolean hasMissingMeasure(RegionJobStatisticsCsvRow row) {
        return row.jobOpenings() == null || row.jobSeekers() == null || row.placements() == null
                || row.validOpenings() == null || row.validSeekers() == null;
    }

    private boolean isKnownSigunguCode(String sigunguCode) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = addressQueryService.getAllSigunguCodes().stream()
                    .map(SigunguCode::value)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(sigunguCode);
    }

    private boolean isKnownJobTopCode(String jobTopCode) {
        if (jobTopCodeCache == null) {
            jobTopCodeCache = jobCodeTopJpaRepository.findAll().stream()
                    .map(JobCodeTopJpaEntity::getCode)
                    .collect(Collectors.toSet());
        }
        return jobTopCodeCache.contains(jobTopCode);
    }

    private static Long parseLongOrNull(String raw) {
        String digits = digitsOnly(raw);
        if (digits == null || digits.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
