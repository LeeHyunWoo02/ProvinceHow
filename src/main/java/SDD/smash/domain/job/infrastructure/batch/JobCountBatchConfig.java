package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.infrastructure.batch.dto.JobCountCsvRow;
import SDD.smash.domain.job.infrastructure.batch.dto.JobCountUpsertRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeMiddleJpaEntity;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeMiddleJpaRepository;
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
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Set;
import java.util.stream.Collectors;

import static SDD.smash.global.util.BatchTextUtil.addLeadingZeroThird;
import static SDD.smash.global.util.BatchTextUtil.isBlank;
import static SDD.smash.global.util.BatchTextUtil.normalize;

/**
 * 일자리 수 적재 배치. As-Is {@code JobCountBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("jobCountJob")과 빈 이름, chunk 크기, CSV 인코딩({@code MS949}),
 * Upsert SQL 을 그대로 유지한다. 테이블명 {@code JobCount} 도 As-Is 그대로다.
 *
 * <p>시군구 목록은 address 컨텍스트의 application Service 에서 받는다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class JobCountBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobCodeMiddleJpaRepository jobCodeMiddleJpaRepository;
    private final AddressQueryService addressQueryService;
    private final JobScoreCacheCleaner jobScoreCacheCleaner;
    private final WorknetJobCountItemReader worknetJobCountItemReader;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    private Set<String> sigunguCodeCache = null;
    private Set<String> middleCodeCache = null;

    private boolean isKnownSigunguCode(String sigunguCode) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = addressQueryService.getAllSigunguCodes()
                    .stream()
                    .map(SigunguCode::value)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(sigunguCode);
    }

    private boolean isKnownMiddleCode(String middleCode) {
        if (middleCodeCache == null) {
            middleCodeCache = jobCodeMiddleJpaRepository.findAll()
                    .stream()
                    .map(JobCodeMiddleJpaEntity::getCode)
                    .collect(Collectors.toSet());
        }
        return middleCodeCache.contains(middleCode);
    }

    @Value("${jobCount.filePath}")
    private String filePath;

    /**
     * 적재 소스 스위치. 기본은 <b>워크넷 채용정보 API</b> 다.
     * {@code false} 로 두면 예전 {@code jobCount.filePath} CSV 경로로 되돌아간다(레거시 옵션).
     */
    @Value("${worknet.job.batch.enabled:true}")
    private boolean worknetApiEnabled;

    @Bean
    public Job jobCountJob() {
        return new JobBuilder("jobCountJob", jobRepository)
                .listener(jobScoreCacheCleaner)
                .start(jobCountStep())
                .build();
    }

    @Bean
    public Step jobCountStep() {

        return new StepBuilder("jobCountStep", jobRepository)
                .<JobCountCsvRow, JobCountUpsertRow> chunk(1000, platformTransactionManager)
                .reader(jobCountSourceReader())
                .processor(jobCountCsvProcessor())
                .writer(jobCountWriter())
                .build();
    }

    /**
     * Reader 만 갈아끼운다. Processor(시군구/직종 FK 검증)와 Writer(Upsert SQL)는 두 경로가 공유한다.
     *
     * <p>빈으로 등록하지 않는다 — 두 Reader 중 하나만 Step 에 들어가야 하고,
     * 빈이 되면 쓰이지 않는 쪽까지 컨텍스트에 남아 오해를 만든다.
     */
    private ItemReader<JobCountCsvRow> jobCountSourceReader() {
        if (worknetApiEnabled) {
            log.info("[jobCountStep] 적재 소스 = 워크넷 채용정보 API");
            return worknetJobCountItemReader;
        }
        log.info("[jobCountStep] 적재 소스 = 레거시 CSV (worknet.job.batch.enabled=false)");
        return jobCountCsvReader();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<JobCountCsvRow> jobCountCsvReader() {

        return new FlatFileItemReaderBuilder<JobCountCsvRow>()
                .name("jobCountCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "job_code", "count")
                .fieldSetMapper(fieldSet -> {
                    String sigunguCode = normalize(fieldSet.readString(0));
                    String middleCode = normalize(fieldSet.readString(1));
                    Integer count = Integer.parseInt(normalize(fieldSet.readString(2)));

                    return new JobCountCsvRow(sigunguCode, middleCode, count);
                })
                .build();
    }

    @Bean
    public ItemProcessor<JobCountCsvRow, JobCountUpsertRow> jobCountCsvProcessor() {
        return row -> {
            String sigunguCode = row.sigunguCode();
            String middleCode = addLeadingZeroThird(row.middleCode());
            if (isBlank(sigunguCode) || !isKnownSigunguCode(sigunguCode)) {
                return null;
            } else if (isBlank(middleCode) || !isKnownMiddleCode(middleCode)) {
                return null;
            }
            return JobCountUpsertRow.builder()
                    .sigunguCode(sigunguCode)
                    .middleCode(middleCode)
                    .count(row.count())
                    .build();
        };
    }

    @Bean
    public JdbcBatchItemWriter<JobCountUpsertRow> jobCountWriter() {

        String upsertSql = """
            INSERT INTO JobCount (sigungu_code, job_code_middle_code, count)
            VALUES (:sigunguCode, :middleCode, :count)
            ON DUPLICATE KEY UPDATE count = VALUES(count)
            """;

        return new JdbcBatchItemWriterBuilder<JobCountUpsertRow>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }
}
