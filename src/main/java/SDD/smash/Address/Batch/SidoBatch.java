package SDD.smash.Address.Batch;

import SDD.smash.Address.Converter.AddressConverter;
import SDD.smash.Address.Dto.SidoDTO;
import SDD.smash.Address.Entity.Sido;
import SDD.smash.Address.Repository.SidoRepository;
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



@Configuration
@Slf4j
public class SidoBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SidoRepository sidoRepository;

    public SidoBatch(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager, SidoRepository sidoRepository) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.sidoRepository = sidoRepository;
    }

    @Value("${sido.filePath}")
    private String filePath;

    @Bean
    public Job SidoJob(){
        return new JobBuilder("SidoJob", jobRepository)
                .start(SidoStep())
                .build();
    }

    @Bean
    public Step SidoStep() {

        return new StepBuilder("SidoStep", jobRepository)
                .<SidoDTO, Sido> chunk(10, platformTransactionManager)
                .reader(sidoCsvReader())
                .processor(sidoCsvProcessor())
                .writer(SidoWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<SidoDTO> sidoCsvReader() {

        return new FlatFileItemReaderBuilder<SidoDTO>()
                .name("sidoCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sido_code", "name")
                .fieldSetMapper(fieldSet -> {
                    SidoDTO dto = new SidoDTO();
                    dto.setSido_code(fieldSet.readString(0).trim());
                    dto.setName(fieldSet.readString(1).trim());
                    return dto;
                })
                .build();
    }

    @Bean
    public ItemProcessor<SidoDTO, Sido> sidoCsvProcessor(){
        return AddressConverter::sidoToEntity;
    }

    @Bean
    public RepositoryItemWriter<Sido> SidoWriter() {

        return new RepositoryItemWriterBuilder<Sido>()
                .repository(sidoRepository)
                .methodName("save")
                .build();
    }



}
