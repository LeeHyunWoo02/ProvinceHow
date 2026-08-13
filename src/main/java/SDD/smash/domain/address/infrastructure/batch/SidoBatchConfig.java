package SDD.smash.domain.address.infrastructure.batch;

import SDD.smash.domain.address.infrastructure.batch.dto.SidoCsvRow;
import SDD.smash.domain.address.infrastructure.persistence.SidoJpaEntity;
import SDD.smash.domain.address.infrastructure.persistence.SidoJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
 * 시도 시드 배치. As-Is {@code SidoBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("SidoJob")과 빈 이름은 바꾸지 않는다.
 * Step 빈 이름("SidoStep")은 {@code seedMasterJob} 이 {@code @Qualifier} 로 찾고
 * {@code BatchGuard} 가 STEP_NAME 으로 재실행 여부를 판단하므로 바꾸지 않는다.
 */
@Configuration
@Slf4j
public class SidoBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SidoJpaRepository sidoJpaRepository;

    public SidoBatchConfig(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager,
                           SidoJpaRepository sidoJpaRepository) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.sidoJpaRepository = sidoJpaRepository;
    }

    @Value("${sido.filePath}")
    private String filePath;

    @Bean
    public Job SidoJob() {
        return new JobBuilder("SidoJob", jobRepository)
                .start(SidoStep())
                .build();
    }

    @Bean
    public Step SidoStep() {

        return new StepBuilder("SidoStep", jobRepository)
                .<SidoCsvRow, SidoJpaEntity> chunk(10, platformTransactionManager)
                .reader(sidoCsvReader())
                .processor(sidoCsvProcessor())
                .writer(SidoWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<SidoCsvRow> sidoCsvReader() {

        return new FlatFileItemReaderBuilder<SidoCsvRow>()
                .name("sidoCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sido_code", "name")
                .fieldSetMapper(fieldSet -> new SidoCsvRow(
                        fieldSet.readString(0).trim(),
                        fieldSet.readString(1).trim()))
                .build();
    }

    @Bean
    public ItemProcessor<SidoCsvRow, SidoJpaEntity> sidoCsvProcessor() {
        return AddressCsvMapper::toSidoJpaEntity;
    }

    @Bean
    public RepositoryItemWriter<SidoJpaEntity> SidoWriter() {

        return new RepositoryItemWriterBuilder<SidoJpaEntity>()
                .repository(sidoJpaRepository)
                .methodName("save")
                .build();
    }
}
