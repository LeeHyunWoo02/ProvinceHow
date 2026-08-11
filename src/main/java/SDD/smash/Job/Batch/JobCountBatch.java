package SDD.smash.Job.Batch;


import SDD.smash.Address.Entity.Sigungu;
import SDD.smash.Address.Repository.SigunguRepository;
import SDD.smash.Job.Dto.JobCountCsvDTO;
import SDD.smash.Job.Dto.JobCountUpsertDTO;
import SDD.smash.Job.Entity.JobCodeMiddle;
import SDD.smash.Job.Repository.JobCodeMiddleRepository;
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

import static SDD.smash.Util.BatchTextUtil.*;


@Configuration
@Slf4j
@RequiredArgsConstructor
public class JobCountBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobCodeMiddleRepository jobCodeMiddleRepository;
    private final SigunguRepository sigunguRepository;
    private final JobCacheCleaner jobCacheCleaner;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;


    private Set<String> sigunguCodeCache = null;
    private Set<String> middleCodeCache = null;

    private boolean isKnownSigunguCode(String sigunguCode) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = sigunguRepository.findAll()
                    .stream()
                    .map(Sigungu::getSigunguCode)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(sigunguCode);
    }

    private boolean isKnownMiddleCode(String middleCode) {
        if (middleCodeCache == null) {
            middleCodeCache = jobCodeMiddleRepository.findAll()
                    .stream()
                    .map(JobCodeMiddle::getCode)
                    .collect(Collectors.toSet());
        }
        return middleCodeCache.contains(middleCode);
    }

    @Value("${jobCount.filePath}")
    private String filePath;

    @Bean
    public Job jobCountJob(){
        return new JobBuilder("jobCountJob", jobRepository)
                .listener(jobCacheCleaner)
                .start(jobCountStep())
                .build();
    }

    @Bean
    public Step jobCountStep() {

        return new StepBuilder("jobCountStep", jobRepository)
                .<JobCountCsvDTO, JobCountUpsertDTO> chunk(1000, platformTransactionManager)
                .reader(jobCountCsvReader())
                .processor(jobCountCsvProcessor())
                .writer(jobCountWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<JobCountCsvDTO> jobCountCsvReader() {

        return new FlatFileItemReaderBuilder<JobCountCsvDTO>()
                .name("jobCountCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "job_code","count")
                .fieldSetMapper(fieldSet -> {
                    String sigungu_code = normalize(fieldSet.readString(0));
                    String middle_code = normalize(fieldSet.readString(1));
                    Integer count = Integer.parseInt(normalize(fieldSet.readString(2)));

                    return new JobCountCsvDTO(sigungu_code, middle_code, count);
                })
                .build();
    }

    @Bean
    public ItemProcessor<JobCountCsvDTO, JobCountUpsertDTO> jobCountCsvProcessor(){
        return dto -> {
            String sigunguCode = dto.getSigungu_code();
            String middleCode  = addLeadingZeroThird(dto.getMiddle_code());
            if(isBlank(sigunguCode) || !isKnownSigunguCode(sigunguCode)){
                return null;
            } else if(isBlank(middleCode) || !isKnownMiddleCode(middleCode)){
                return null;
            }
            return JobCountUpsertDTO.builder()
                    .sigunguCode(sigunguCode)
                    .middleCode(middleCode)
                    .count(dto.getCount())
                    .build();
        };
    }
    @Bean
    public JdbcBatchItemWriter<JobCountUpsertDTO> jobCountWriter() {

        String upsertSql = """
            INSERT INTO JobCount (sigungu_code, job_code_middle_code, count)
            VALUES (:sigunguCode, :middleCode, :count)
            ON DUPLICATE KEY UPDATE count = VALUES(count)
            """;

        return new JdbcBatchItemWriterBuilder<JobCountUpsertDTO>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }

}
