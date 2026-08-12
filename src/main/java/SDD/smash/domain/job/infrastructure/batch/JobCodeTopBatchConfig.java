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

/**
 * 직종 대분류 시드 배치. As-Is {@code JobCodeTopBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("jcTopJob")과 빈 이름, chunk 크기, CSV 인코딩({@code MS949})을 그대로 유지한다.
 * {@code BatchGuard} 가 Job 이름으로 재실행 여부를 판단하고 Runner 가 {@code @Qualifier} 로 찾는다.
 */
@Slf4j
@Configuration
public class JobCodeTopBatchConfig {

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
                .encoding("MS949")
                .linesToSkip(1)
                .skippedLinesCallback(line -> log.info("Skip header : {}", line))
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("jobCode", "name")
                .fieldSetMapper(fieldSet -> new JobCodeTopCsvRow(
                        fieldSet.readString(0).trim(),
                        fieldSet.readString(1).trim()))
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
