package SDD.smash.Job.Batch;


import SDD.smash.Job.Converter.JobConverter;
import SDD.smash.Job.Dto.JobCodeMiddleDTO;
import SDD.smash.Job.Entity.JobCodeMiddle;
import SDD.smash.Job.Entity.JobCodeTop;
import SDD.smash.Job.Repository.JobCodeMiddleRepository;
import SDD.smash.Job.Repository.JobCodeTopRepository;
<<<<<<< HEAD
import lombok.RequiredArgsConstructor;
=======
>>>>>>> origin/Backup/main
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

<<<<<<< HEAD
=======
import java.util.Arrays;
>>>>>>> origin/Backup/main
import java.util.HashMap;
import java.util.Map;

import static SDD.smash.Util.BatchTextUtil.addLeadingZero;
import static SDD.smash.Util.BatchTextUtil.normalize;


@Configuration
@Slf4j
<<<<<<< HEAD
@RequiredArgsConstructor
=======
>>>>>>> origin/Backup/main
public class JobCodeMiddleBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobCodeMiddleRepository jobCodeMiddleRepository;
    private final JobCodeTopRepository jobCodeTopRepository;


<<<<<<< HEAD
=======
    public JobCodeMiddleBatch(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager, JobCodeMiddleRepository jobCodeMiddleRepository, JobCodeTopRepository jobCodeTopRepository) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.jobCodeMiddleRepository = jobCodeMiddleRepository;
        this.jobCodeTopRepository = jobCodeTopRepository;
    }

>>>>>>> origin/Backup/main
    @Value("${jobCodeMiddle.filePath}")
    private String filePath;

    private Map<String, JobCodeTop> JobCodeTopCache = null;

    private JobCodeTop resolveSido(String jobCodeTop) {
        if (JobCodeTopCache == null) {
            JobCodeTopCache = new HashMap<>();
            for (JobCodeTop jct : jobCodeTopRepository.findAll()) {
                JobCodeTopCache.put(jct.getCode(), jct);
            }
        }
        return JobCodeTopCache.get(jobCodeTop);
    }

    @Bean
    public Job jcMiddleJob(){
        return new JobBuilder("jcMiddleJob", jobRepository)
                .start(jcMiddleStep())
                .build();
    }

    @Bean
    public Step jcMiddleStep(){
        return new StepBuilder("jcMiddleStep", jobRepository)
                .<JobCodeMiddleDTO, JobCodeMiddle> chunk(100, platformTransactionManager)
                .reader(jcMiddleCsvReader())
                .processor(jcMiddleProcessor())
                .writer(jcMiddleWriter())
                .build();
    }

    @Bean
    public FlatFileItemReader<JobCodeMiddleDTO> jcMiddleCsvReader(){

        return new FlatFileItemReaderBuilder<JobCodeMiddleDTO>()
                .name("jcMiddleCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .skippedLinesCallback(line -> log.info("Skip header : {}", line))
                .strict(true)
<<<<<<< HEAD
                .delimited()
                    .delimiter(",")
                    .quoteCharacter('\0')
                    .includedFields(0,1,2)
                    .strict(false)
                    .names("jobCode", "name","upstream_code")
                .fieldSetMapper(fieldSet -> {
                    JobCodeMiddleDTO dto = new JobCodeMiddleDTO();
                    dto.setCode(fieldSet.readString(0).trim());
                    dto.setName(fieldSet.readString(1).trim());
                    dto.setUpstream(fieldSet.readString(2).trim());
=======
                .lineMapper((line, lineNumber) -> {
                    String[] values = line.split(",", -1);
                    JobCodeMiddleDTO dto = new JobCodeMiddleDTO();
                    dto.setCode(values[0].trim());
                    dto.setName(String.join(",", Arrays.copyOfRange(values, 1, values.length - 1)).trim());
                    dto.setUpstream(values[values.length - 1].trim());
>>>>>>> origin/Backup/main

                    return dto;
                })
                .build();
    }

    @Bean
    public ItemProcessor<JobCodeMiddleDTO, JobCodeMiddle> jcMiddleProcessor(){
        return dto -> {
            String jobCodeTop = addLeadingZero(normalize(dto.getUpstream()));
            JobCodeTop jct = resolveSido(jobCodeTop);
<<<<<<< HEAD

=======
>>>>>>> origin/Backup/main
            if (jct == null) {
                return null;
            }
            return JobConverter.middleToEntity(dto,jct);
        };
    }

    @Bean
    public RepositoryItemWriter<JobCodeMiddle> jcMiddleWriter(){
        return new RepositoryItemWriterBuilder<JobCodeMiddle>()
                .repository(jobCodeMiddleRepository)
                .methodName("save")
                .build();
    }


}
