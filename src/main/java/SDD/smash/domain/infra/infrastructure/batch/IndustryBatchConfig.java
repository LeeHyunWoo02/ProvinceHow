package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.infrastructure.batch.dto.IndustryCsvRow;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaEntity;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaRepository;
import lombok.RequiredArgsConstructor;
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
 * 업종 마스터 시드 배치. As-Is {@code IndustryBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("industryJob")과 빈 이름, chunk 크기, CSV 인코딩(<b>UTF-8</b>)을 그대로 유지한다.
 */
@Configuration
@RequiredArgsConstructor
public class IndustryBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final IndustryJpaRepository industryJpaRepository;

    @Value("${industry.filePath}")
    private String filePath;

    @Bean
    public Job industryJob() {
        return new JobBuilder("industryJob", jobRepository)
                .start(industryStep())
                .build();
    }

    @Bean
    public Step industryStep() {

        return new StepBuilder("industryStep", jobRepository)
                .<IndustryCsvRow, IndustryJpaEntity> chunk(20, platformTransactionManager)
                .reader(industryCsvReader())
                .processor(industryCsvProcessor())
                .writer(industryWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<IndustryCsvRow> industryCsvReader() {

        return new FlatFileItemReaderBuilder<IndustryCsvRow>()
                .name("industryCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("code", "name", "major")
                .fieldSetMapper(fieldSet -> new IndustryCsvRow(
                        fieldSet.readString(0).trim(),
                        fieldSet.readString(1).trim(),
                        fieldSet.readString(2).trim()))
                .build();
    }

    @Bean
    public ItemProcessor<IndustryCsvRow, IndustryJpaEntity> industryCsvProcessor() {
        return InfraCsvMapper::toIndustryJpaEntity;
    }

    @Bean
    public RepositoryItemWriter<IndustryJpaEntity> industryWriter() {

        return new RepositoryItemWriterBuilder<IndustryJpaEntity>()
                .repository(industryJpaRepository)
                .methodName("save")
                .build();
    }
}
