package SDD.smash.address.infrastructure.batch;

import SDD.smash.address.infrastructure.batch.dto.PopulationCsvRow;
import SDD.smash.address.infrastructure.batch.dto.PopulationUpsertRow;
import SDD.smash.address.infrastructure.persistence.SigunguJpaEntity;
import SDD.smash.address.infrastructure.persistence.SigunguJpaRepository;
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

import static SDD.smash.common.util.BatchTextUtil.digitsOnly;
import static SDD.smash.common.util.BatchTextUtil.isBlank;
import static SDD.smash.common.util.BatchTextUtil.normalize;

/**
 * 인구 시드 배치. As-Is {@code PopulationBatch} 를 옮긴 것이다.
 *
 * <p>Upsert SQL 문자열과 네임드 파라미터는 한 글자도 바꾸지 않았다.
 * 테이블명 {@code Population} 도 As-Is 그대로다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class PopulationBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SigunguJpaRepository sigunguJpaRepository;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    private Set<String> sigunguCodeCache = null;

    private boolean isKnownSigunguCode(String code) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = sigunguJpaRepository.findAll()
                    .stream()
                    .map(SigunguJpaEntity::getSigunguCode)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(code);
    }

    @Value("${population.filePath}")
    private String filePath;

    @Bean
    public Job PopulationJob() {
        return new JobBuilder("PopulationJob", jobRepository)
                .start(populationStep())
                .build();
    }

    @Bean
    public Step populationStep() {

        return new StepBuilder("populationStep", jobRepository)
                .<PopulationCsvRow, PopulationUpsertRow> chunk(100, platformTransactionManager)
                .reader(populationCsvReader())
                .processor(populationCsvProcessor())
                .writer(populationWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<PopulationCsvRow> populationCsvReader() {

        return new FlatFileItemReaderBuilder<PopulationCsvRow>()
                .name("populationCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "population")
                .fieldSetMapper(fieldSet -> {
                    String sigunguCode = normalize(fieldSet.readString(0));
                    String pop = digitsOnly(fieldSet.readString(1));

                    return new PopulationCsvRow(sigunguCode, pop);
                })
                .build();
    }

    @Bean
    public ItemProcessor<PopulationCsvRow, PopulationUpsertRow> populationCsvProcessor() {
        return row -> {
            String sigunguCode = row.sigunguCode();
            if (isBlank(sigunguCode) || !isKnownSigunguCode(sigunguCode)) return null;
            return PopulationUpsertRow.builder()
                    .sigunguCode(sigunguCode)
                    .population(row.population())
                    .build();
        };
    }

    @Bean
    public JdbcBatchItemWriter<PopulationUpsertRow> populationWriter() {
        String upsertSql = """
        INSERT INTO Population (sigungu_code, population_count)
        VALUES (:sigunguCode, :population)
        ON DUPLICATE KEY UPDATE population_count = VALUES(population_count)
            """;

        return new JdbcBatchItemWriterBuilder<PopulationUpsertRow>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }
}
