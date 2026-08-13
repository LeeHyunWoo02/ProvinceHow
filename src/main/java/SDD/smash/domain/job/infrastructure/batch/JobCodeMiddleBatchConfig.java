package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.infrastructure.batch.dto.JobCodeMiddleCsvRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeMiddleJpaEntity;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeMiddleJpaRepository;
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
import java.util.HashSet;
import java.util.Set;

import static SDD.smash.global.util.BatchTextUtil.addLeadingZero;
import static SDD.smash.global.util.BatchTextUtil.normalize;

/**
 * 직종 중분류 시드 배치. As-Is {@code JobCodeMiddleBatch} 를 옮긴 것이다.
 *
 * <p>As-Is 는 {@code Map<String, JobCodeTop>} 으로 대분류 엔티티를 캐싱해 {@code @ManyToOne} 에 물렸다.
 * FK 객체 참조를 없앴으므로 <b>코드 집합</b>만 캐싱해 존재 여부를 판정한다.
 * 대분류를 찾지 못하면 해당 행을 skip 하는 것까지 As-Is 와 같다.
 *
 * <p>CSV 는 <b>UTF-8(BOM 없음)</b> 이고 이름에 쉼표가 들어가는 행은 표준 CSV 큰따옴표로 감싸져 있다.
 * 따라서 직접 문자열을 자르던 {@code lineMapper} 대신 따옴표를 해석하는
 * {@code delimited()} 토크나이저를 쓴다. 열 개수가 어긋난 행은 조용히 넘기지 않고
 * {@code FlatFileParseException} 으로 배치를 실패시킨다 — 시드가 조용히 누락되는 편이 더 위험하다.
 */
@Configuration
@Slf4j
public class JobCodeMiddleBatchConfig {

    /** {@code code,name,upstream_code} */
    private static final int MIDDLE_COLUMN_COUNT = 3;

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
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .skippedLinesCallback(line -> log.info("Skip header : {}", line))
                .strict(true)
                .delimited()
                .delimiter(",")
                .names("code", "name", "upstreamCode")
                .fieldSetMapper(fieldSet -> {
                    if (fieldSet.getFieldCount() != MIDDLE_COLUMN_COUNT) {
                        throw new IllegalArgumentException(
                                "직종 중분류 CSV 는 %d 개 열이어야 한다. 실제=%d"
                                        .formatted(MIDDLE_COLUMN_COUNT, fieldSet.getFieldCount()));
                    }
                    return new JobCodeMiddleCsvRow(
                            fieldSet.readString(0).trim(),
                            fieldSet.readString(1).trim(),
                            fieldSet.readString(2).trim());
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
