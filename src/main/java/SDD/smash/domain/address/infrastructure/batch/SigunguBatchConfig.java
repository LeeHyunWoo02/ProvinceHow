package SDD.smash.domain.address.infrastructure.batch;

import SDD.smash.domain.address.infrastructure.batch.dto.SigunguCsvRow;
import SDD.smash.domain.address.infrastructure.persistence.SidoJpaEntity;
import SDD.smash.domain.address.infrastructure.persistence.SidoJpaRepository;
import SDD.smash.domain.address.infrastructure.persistence.SigunguJpaEntity;
import SDD.smash.domain.address.infrastructure.persistence.SigunguJpaRepository;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashSet;
import java.util.Set;

import static SDD.smash.global.util.BatchTextUtil.addLeadingZero;
import static SDD.smash.global.util.BatchTextUtil.normalize;

/**
 * 시군구 시드 배치. As-Is {@code SigunguBatch} 를 옮긴 것이다.
 *
 * <p>As-Is 는 {@code Map<String, Sido>} 로 시도 엔티티를 캐싱해 {@code @ManyToOne} 에 물렸다.
 * FK 객체 참조를 없앴으므로 <b>코드 집합</b>만 캐싱해 존재 여부를 판정한다.
 * 시도를 찾지 못하면 {@code sido_code} 에 null 이 들어가 flush 에서 실패하는 것까지 As-Is 와 같다.
 */
@Configuration
@Slf4j
public class SigunguBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SidoJpaRepository sidoJpaRepository;
    private final SigunguJpaRepository sigunguJpaRepository;

    // SigunguWriter 가 smash_data 에 쓰므로 청크 트랜잭션 매니저를 dataTransactionManager 로 못 박는다.
    // 필드 @Qualifier 는 lombok 이 생성자에 복사하지 않아 무효이므로 파라미터에 붙인다.
    public SigunguBatchConfig(JobRepository jobRepository,
                              @Qualifier("dataTransactionManager") PlatformTransactionManager platformTransactionManager,
                              SidoJpaRepository sidoJpaRepository, SigunguJpaRepository sigunguJpaRepository) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.sidoJpaRepository = sidoJpaRepository;
        this.sigunguJpaRepository = sigunguJpaRepository;
    }

    @Value("${sigungu.filePath}")
    private String filePath;

    @Bean
    public Job SigunguJob() {
        return new JobBuilder("SigunguJob", jobRepository)
                .start(SigunguStep())
                .build();
    }

    @Bean
    public Step SigunguStep() {

        return new StepBuilder("SigunguStep", jobRepository)
                .<SigunguCsvRow, SigunguJpaEntity> chunk(50, platformTransactionManager)
                .reader(sigunguCsvReader())
                .processor(sigungoCsvProcessor())
                .writer(SigunguWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<SigunguCsvRow> sigunguCsvReader() {

        return new FlatFileItemReaderBuilder<SigunguCsvRow>()
                .name("sigunguCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("UTF-8")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "sido_code", "name")
                .fieldSetMapper(fieldSet -> new SigunguCsvRow(
                        fieldSet.readString(0).trim(),
                        fieldSet.readString(1).trim(),
                        fieldSet.readString(2).trim()))
                .build();
    }

    /**
     * 빈 이름은 As-Is 의 오타(sigungo)를 그대로 둔다. 이름을 바꾸는 것은 이관 범위가 아니다.
     *
     * <p>시도 코드 집합은 Step 실행마다 새로 조회한다. @StepScope 라 재실행 때 stale 캐시가 남지 않는다.
     * 등록된 시도 코드면 그대로, 아니면 null 을 넘겨 As-Is {@code resolveSido} 와 판정을 맞춘다.
     */
    @Bean
    @StepScope
    public ItemProcessor<SigunguCsvRow, SigunguJpaEntity> sigungoCsvProcessor() {
        Set<String> sidoCodes = new HashSet<>();
        for (SidoJpaEntity s : sidoJpaRepository.findAll()) {
            sidoCodes.add(s.getSidoCode());
        }
        return row -> {
            String sidoCode = addLeadingZero(normalize(row.sidoCode()));
            if (sidoCode == null) {
                log.warn("Empty sido key. Skip row.");
                return null;
            }
            String resolvedSidoCode = sidoCodes.contains(sidoCode) ? sidoCode : null;
            return AddressCsvMapper.toSigunguJpaEntity(row, resolvedSidoCode);
        };
    }

    @Bean
    public RepositoryItemWriter<SigunguJpaEntity> SigunguWriter() {

        return new RepositoryItemWriterBuilder<SigunguJpaEntity>()
                .repository(sigunguJpaRepository)
                .methodName("save")
                .build();
    }
}
