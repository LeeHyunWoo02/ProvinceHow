package SDD.smash.infra.infrastructure.batch;

import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.infra.infrastructure.batch.dto.InfraCsvRow;
import SDD.smash.infra.infrastructure.batch.dto.InfraUpsertRow;
import SDD.smash.infra.infrastructure.persistence.IndustryJpaEntity;
import SDD.smash.infra.infrastructure.persistence.IndustryJpaRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.stream.Collectors;

import static SDD.smash.common.util.BatchTextUtil.isBlank;
import static SDD.smash.common.util.BatchTextUtil.normalize;

/**
 * 인프라 적재 배치. As-Is {@code InfraBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("infraJob")과 빈 이름, chunk 크기, CSV 인코딩(<b>MS949</b>), Upsert SQL 을
 * 그대로 유지한다. {@code data/infra.csv} 에 UTF-8 BOM 이 있어도 헤더 줄만 오염되고
 * 헤더는 {@code linesToSkip(1)} 으로 건너뛰므로 데이터 행에는 영향이 없다(As-Is 에서 실측 확인됨).
 *
 * <p>시군구 목록은 옛 {@code SigunguRepository} 대신 address 의 in-port 에서 받는다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class InfraBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final IndustryJpaRepository industryJpaRepository;
    private final AddressQueryUseCase addressQueryUseCase;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    private Set<String> sigunguCodeCache = null;
    private Set<String> industryCodeCache = null;

    private boolean isKnownSigunguCode(String sigunguCode) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = addressQueryUseCase.getAllSigunguCodes()
                    .stream()
                    .map(SigunguCode::value)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(sigunguCode);
    }

    private boolean isKnownIndustryCode(String industryCode) {
        if (industryCodeCache == null) {
            industryCodeCache = industryJpaRepository.findAll()
                    .stream()
                    .map(IndustryJpaEntity::getCode)
                    .collect(Collectors.toSet());
        }
        return industryCodeCache.contains(industryCode);
    }

    @Value("${infra.filePath}")
    private String filePath;

    @Bean
    public Job infraJob() {
        return new JobBuilder("infraJob", jobRepository)
                .start(infraStep())
                .build();
    }

    @Bean
    public Step infraStep() {

        return new StepBuilder("infraStep", jobRepository)
                .<InfraCsvRow, InfraUpsertRow> chunk(500, platformTransactionManager)
                .reader(infraCsvReader())
                .processor(infraCsvProcessor())
                .writer(infraWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<InfraCsvRow> infraCsvReader() {

        return new FlatFileItemReaderBuilder<InfraCsvRow>()
                .name("infraCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "industry_code", "count", "ratio", "score")
                .fieldSetMapper(fieldSet -> {
                    String rawSigunguCode = normalize(fieldSet.readString(0));
                    String rawIndustryCode = normalize(fieldSet.readString(1));
                    String rawCount = normalize(fieldSet.readString(2));
                    BigDecimal rawRatio = new BigDecimal(normalize(fieldSet.readString(3)))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal rawScore = new BigDecimal(normalize(fieldSet.readString(4)))
                            .setScale(2, RoundingMode.HALF_UP);

                    return new InfraCsvRow(rawSigunguCode, rawIndustryCode, rawCount, rawRatio, rawScore);
                })
                .build();
    }

    @Bean
    public ItemProcessor<InfraCsvRow, InfraUpsertRow> infraCsvProcessor() {
        return row -> {
            String sigunguCode = row.sigunguCode();
            String industryCode = row.industryCode();
            if (isBlank(sigunguCode) || !isKnownSigunguCode(sigunguCode)) {
                return null;
            } else if (isBlank(industryCode) || !isKnownIndustryCode(industryCode)) {
                return null;
            }
            return InfraUpsertRow.builder()
                    .sigunguCode(sigunguCode)
                    .industryCode(industryCode)
                    .count(row.countRaw())
                    .ratio(row.ratio())
                    .score(row.score())
                    .build();
        };
    }

    @Bean
    public JdbcBatchItemWriter<InfraUpsertRow> infraWriter() {
        String upsertSql = """
            INSERT INTO infra (sigungu_code, industry_code, count, ratio, score)
            VALUES (:sigunguCode, :industryCode, :count, :ratio, :score)
            ON DUPLICATE KEY UPDATE count = VALUES(count)
            """;

        return new JdbcBatchItemWriterBuilder<InfraUpsertRow>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }
}
