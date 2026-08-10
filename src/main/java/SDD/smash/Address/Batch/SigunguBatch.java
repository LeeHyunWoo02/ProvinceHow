package SDD.smash.Address.Batch;

import SDD.smash.Address.Converter.AddressConverter;
import SDD.smash.Address.Dto.SigunguDTO;
import SDD.smash.Address.Entity.Sido;
import SDD.smash.Address.Entity.Sigungu;
import SDD.smash.Address.Repository.SidoRepository;
import SDD.smash.Address.Repository.SigunguRepository;
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

import java.util.HashMap;
import java.util.Map;

import static SDD.smash.Util.BatchTextUtil.*;


@Configuration
@Slf4j
public class SigunguBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SidoRepository sidoRepository;
    private final SigunguRepository sigunguRepository;

    public SigunguBatch(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager, SidoRepository sidoRepository, SigunguRepository sigunguRepository) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.sidoRepository = sidoRepository;
        this.sigunguRepository = sigunguRepository;
    }

    @Value("${sigungu.filePath}")
    private String filePath;

    private Map<String, Sido> sidoCache = null;

    private Sido resolveSido(String sidoCode) {
        if (sidoCache == null) {
            sidoCache = new HashMap<>();
            for (Sido s : sidoRepository.findAll()) {
                sidoCache.put(s.getSidoCode(), s);
            }
        }
        return sidoCache.get(sidoCode);
    }

    @Bean
    public Job SigunguJob(){
        return new JobBuilder("SigunguJob", jobRepository)
                .start(SigunguStep())
                .build();
    }

    @Bean
    public Step SigunguStep() {

        return new StepBuilder("SigunguStep", jobRepository)
                .<SigunguDTO, Sigungu> chunk(50, platformTransactionManager)
                .reader(sigunguCsvReader())
                .processor(sigungoCsvProcessor())
                .writer(SigunguWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<SigunguDTO> sigunguCsvReader() {

        return new FlatFileItemReaderBuilder<SigunguDTO>()
                .name("sigunguCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "sido_code","name")
                .fieldSetMapper(fieldSet -> {
                    SigunguDTO dto = new SigunguDTO();
                    dto.setSigungu_code(fieldSet.readString(0).trim());
                    dto.setSido_code(fieldSet.readString(1).trim());
                    dto.setName(fieldSet.readString(2).trim());
                    return dto;
                })
                .build();
    }

    @Bean
    public ItemProcessor<SigunguDTO, Sigungu> sigungoCsvProcessor(){
        return dto -> {
            String sidoCode = addLeadingZero(normalize(dto.getSido_code()));
            if(sidoCode == null){
                log.warn("❗ Empty sido key. Skip row.");
                return null;
            }
            Sido sido = resolveSido(sidoCode);

            return AddressConverter.sigunguToEntity(dto, sido);
        };
    }

    @Bean
    public RepositoryItemWriter<Sigungu> SigunguWriter() {

        return new RepositoryItemWriterBuilder<Sigungu>()
                .repository(sigunguRepository)
                .methodName("save")
                .build();
    }

}
