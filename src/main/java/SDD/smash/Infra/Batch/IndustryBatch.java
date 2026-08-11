package SDD.smash.Infra.Batch;

import SDD.smash.Infra.Converter.InfraConverter;
import SDD.smash.Infra.Dto.IndustryDTO;
import SDD.smash.Infra.Entity.Industry;
import SDD.smash.Infra.Repository.IndustryRepository;
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


@Configuration
@RequiredArgsConstructor
public class IndustryBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final IndustryRepository industryRepository;


    @Value("${industry.filePath}")
    private String filePath;

    @Bean
    public Job industryJob(){
        return new JobBuilder("industryJob", jobRepository)
                .start(industryStep())
                .build();
    }

    @Bean
    public Step industryStep() {

        return new StepBuilder("industryStep", jobRepository)
                .<IndustryDTO, Industry> chunk(20, platformTransactionManager)
                .reader(industryCsvReader())
                .processor(industryCsvProcessor())
                .writer(industryWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<IndustryDTO> industryCsvReader() {

        return new FlatFileItemReaderBuilder<IndustryDTO>()
                .name("industryCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("code", "name", "major")
                .fieldSetMapper(fieldSet -> {
                    IndustryDTO dto = new IndustryDTO();
                    dto.setCode(fieldSet.readString(0).trim());
                    dto.setName(fieldSet.readString(1).trim());
                    dto.setMajor(fieldSet.readString(2).trim());
                    return dto;
                })
                .build();
    }

    @Bean
    public ItemProcessor<IndustryDTO, Industry> industryCsvProcessor(){
        return InfraConverter::industryToEntity;
    }

    @Bean
    public RepositoryItemWriter<Industry> industryWriter() {

        return new RepositoryItemWriterBuilder<Industry>()
                .repository(industryRepository)
                .methodName("save")
                .build();
    }

}
