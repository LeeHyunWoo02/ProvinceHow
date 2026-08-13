package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.infrastructure.batch.dto.JobCodeTopCsvRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaEntity;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;

/**
 * 직종 대분류 시드 배치. As-Is {@code JobCodeTopBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("jcTopJob")과 빈 이름, chunk 크기를 그대로 유지한다.
 * {@code BatchGuard} 가 Job 이름으로 재실행 여부를 판단하고 Runner 가 {@code @Qualifier} 로 찾는다.
 *
 * <p>CSV 는 <b>UTF-8(BOM 없음)</b> 이며 표준 CSV 큰따옴표를 그대로 해석한다.
 * 열 개수가 어긋난 행은 skip 하지 않고 {@code FlatFileParseException} 으로 배치를 실패시킨다.
 */
@Slf4j
@Configuration
public class JobCodeTopBatchConfig {

    /** {@code code,name} */
    private static final int TOP_COLUMN_COUNT = 2;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobCodeTopJpaRepository jobCodeTopJpaRepository;

    public JobCodeTopBatchConfig(JobRepository jobRepository,
                                 PlatformTransactionManager platformTransactionManager,
                                 JobCodeTopJpaRepository jobCodeTopJpaRepository) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.jobCodeTopJpaRepository = jobCodeTopJpaRepository;
    }

    @Value("${jobCodeTop.filePath}")
    private String filePath;

    @Bean
    public Job jcTopJob() {
        return new JobBuilder("jcTopJob", jobRepository)
                .start(jcTopStep())
                .build();
    }

    @Bean
    public Step jcTopStep() {
        return new StepBuilder("jcTopStep", jobRepository)
                .<JobCodeTopCsvRow, JobCodeTopJpaEntity> chunk(10, platformTransactionManager)
                .reader(jcTopCsvReader())
                .processor(jcTopProcessor())
                .writer(jcTopWriter())
                .build();
    }

    @Bean
    public FlatFileItemReader<JobCodeTopCsvRow> jcTopCsvReader() {

        return new FlatFileItemReaderBuilder<JobCodeTopCsvRow>()
                .name("jcTopCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .skippedLinesCallback(line -> log.info("Skip header : {}", line))
                .strict(true)
                .delimited()
                .delimiter(",")
                .names("code", "name")
                .fieldSetMapper(fieldSet -> {
                    if (fieldSet.getFieldCount() != TOP_COLUMN_COUNT) {
                        throw new IllegalArgumentException(
                                "직종 대분류 CSV 는 %d 개 열이어야 한다. 실제=%d"
                                        .formatted(TOP_COLUMN_COUNT, fieldSet.getFieldCount()));
                    }
                    return new JobCodeTopCsvRow(
                            fieldSet.readString(0).trim(),
                            fieldSet.readString(1).trim());
                })
                .build();
    }

    @Bean
    public ItemProcessor<JobCodeTopCsvRow, JobCodeTopJpaEntity> jcTopProcessor() {
        return JobCsvMapper::toTopJpaEntity;
    }

    @Bean
    public RepositoryItemWriter<JobCodeTopJpaEntity> jcTopWriter() {
        return new RepositoryItemWriterBuilder<JobCodeTopJpaEntity>()
                .repository(jobCodeTopJpaRepository)
                .methodName("save")
                .build();
    }
}
