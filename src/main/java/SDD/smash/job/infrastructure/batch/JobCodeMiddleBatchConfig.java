package SDD.smash.job.infrastructure.batch;

import SDD.smash.job.infrastructure.batch.dto.JobCodeMiddleCsvRow;
import SDD.smash.job.infrastructure.persistence.JobCodeMiddleJpaEntity;
import SDD.smash.job.infrastructure.persistence.JobCodeMiddleJpaRepository;
import SDD.smash.job.infrastructure.persistence.JobCodeTopJpaEntity;
import SDD.smash.job.infrastructure.persistence.JobCodeTopJpaRepository;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static SDD.smash.common.util.BatchTextUtil.addLeadingZero;
import static SDD.smash.common.util.BatchTextUtil.normalize;

/**
 * 직종 중분류 시드 배치. As-Is {@code JobCodeMiddleBatch} 를 옮긴 것이다.
 *
 * <p>As-Is 는 {@code Map<String, JobCodeTop>} 으로 대분류 엔티티를 캐싱해 {@code @ManyToOne} 에 물렸다.
 * FK 객체 참조를 없앴으므로 <b>코드 집합</b>만 캐싱해 존재 여부를 판정한다.
 * 대분류를 찾지 못하면 해당 행을 skip 하는 것까지 As-Is 와 같다.
 *
 * <p>CSV 인코딩({@code MS949})과 이름에 쉼표가 들어갈 수 있는 {@code lineMapper} 처리를 그대로 유지한다.
 */
@Configuration
@Slf4j
public class JobCodeMiddleBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobCodeMiddleJpaRepository jobCodeMiddleJpaRepository;
    private final JobCodeTopJpaRepository jobCodeTopJpaRepository;

    public JobCodeMiddleBatchConfig(JobRepository jobRepository,
                                    PlatformTransactionManager platformTransactionManager,
                                    JobCodeMiddleJpaRepository jobCodeMiddleJpaRepository,
                                    JobCodeTopJpaRepository jobCodeTopJpaRepository) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.jobCodeMiddleJpaRepository = jobCodeMiddleJpaRepository;
        this.jobCodeTopJpaRepository = jobCodeTopJpaRepository;
    }

    @Value("${jobCodeMiddle.filePath}")
    private String filePath;

    private Set<String> topCodeCache = null;

    /** 등록된 대분류 코드면 그대로, 아니면 null. As-Is {@code resolveSido} 와 판정이 같다. */
    private String resolveTopCode(String topCode) {
        if (topCodeCache == null) {
            topCodeCache = new HashSet<>();
            for (JobCodeTopJpaEntity top : jobCodeTopJpaRepository.findAll()) {
                topCodeCache.add(top.getCode());
            }
        }
        return topCodeCache.contains(topCode) ? topCode : null;
    }

    @Bean
    public Job jcMiddleJob() {
        return new JobBuilder("jcMiddleJob", jobRepository)
                .start(jcMiddleStep())
                .build();
    }

    @Bean
    public Step jcMiddleStep() {
        return new StepBuilder("jcMiddleStep", jobRepository)
                .<JobCodeMiddleCsvRow, JobCodeMiddleJpaEntity> chunk(100, platformTransactionManager)
                .reader(jcMiddleCsvReader())
                .processor(jcMiddleProcessor())
                .writer(jcMiddleWriter())
                .build();
    }

    @Bean
    public FlatFileItemReader<JobCodeMiddleCsvRow> jcMiddleCsvReader() {

        return new FlatFileItemReaderBuilder<JobCodeMiddleCsvRow>()
                .name("jcMiddleCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .skippedLinesCallback(line -> log.info("Skip header : {}", line))
                .strict(true)
                .lineMapper((line, lineNumber) -> {
                    String[] values = line.split(",", -1);
                    // 이름 안에 쉼표가 있을 수 있어 가운데 토큰을 전부 이름으로 다시 합친다.
                    String name = String.join(",", Arrays.copyOfRange(values, 1, values.length - 1)).trim();
                    return new JobCodeMiddleCsvRow(
                            values[0].trim(),
                            name,
                            values[values.length - 1].trim());
                })
                .build();
    }

    @Bean
    public ItemProcessor<JobCodeMiddleCsvRow, JobCodeMiddleJpaEntity> jcMiddleProcessor() {
        return row -> {
            String topCode = addLeadingZero(normalize(row.upstream()));
            String resolved = resolveTopCode(topCode);
            if (resolved == null) {
                return null;
            }
            return JobCsvMapper.toMiddleJpaEntity(row, resolved);
        };
    }

    @Bean
    public RepositoryItemWriter<JobCodeMiddleJpaEntity> jcMiddleWriter() {
        return new RepositoryItemWriterBuilder<JobCodeMiddleJpaEntity>()
                .repository(jobCodeMiddleJpaRepository)
                .methodName("save")
                .build();
    }
}
