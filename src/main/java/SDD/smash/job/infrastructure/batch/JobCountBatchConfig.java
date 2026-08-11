package SDD.smash.job.infrastructure.batch;

import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.job.infrastructure.batch.dto.JobCountCsvRow;
import SDD.smash.job.infrastructure.batch.dto.JobCountUpsertRow;
import SDD.smash.job.infrastructure.persistence.JobCodeMiddleJpaEntity;
import SDD.smash.job.infrastructure.persistence.JobCodeMiddleJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
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
import java.util.Set;
import java.util.stream.Collectors;

import static SDD.smash.common.util.BatchTextUtil.addLeadingZeroThird;
import static SDD.smash.common.util.BatchTextUtil.isBlank;
import static SDD.smash.common.util.BatchTextUtil.normalize;

/**
 * 일자리 수 적재 배치. As-Is {@code JobCountBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("jobCountJob")과 빈 이름, chunk 크기, CSV 인코딩({@code MS949}),
 * Upsert SQL 을 그대로 유지한다. 테이블명 {@code JobCount} 도 As-Is 그대로다.
 *
 * <p>시군구 목록은 옛 {@code SigunguRepository} 대신 address 의 in-port 에서 받는다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class JobCountBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobCodeMiddleJpaRepository jobCodeMiddleJpaRepository;
    private final AddressQueryUseCase addressQueryUseCase;
    private final JobScoreCacheCleaner jobScoreCacheCleaner;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    private Set<String> sigunguCodeCache = null;
    private Set<String> middleCodeCache = null;

    private boolean isKnownSigunguCode(String sigunguCode) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = addressQueryUseCase.getAllSigunguCodes()
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
                .reader(jobCountCsvReader())
                .processor(jobCountCsvProcessor())
                .writer(jobCountWriter())
                .build();
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
