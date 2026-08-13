package SDD.smash.domain.address.infrastructure.batch;

import SDD.smash.domain.address.infrastructure.batch.dto.PopulationCsvRow;
import SDD.smash.domain.address.infrastructure.batch.dto.PopulationUpsertRow;
import SDD.smash.domain.address.infrastructure.persistence.SigunguJpaEntity;
import SDD.smash.domain.address.infrastructure.persistence.SigunguJpaRepository;
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

import static SDD.smash.global.util.BatchTextUtil.digitsOnly;
import static SDD.smash.global.util.BatchTextUtil.isBlank;
import static SDD.smash.global.util.BatchTextUtil.normalize;

/**
 * 인구 시드 배치. As-Is {@code PopulationBatch} 를 옮긴 것이다.
 *
 * <p>네임드 파라미터는 As-Is 그대로다.
 *
 * <p><b>테이블명은 {@code population}(소문자)이다.</b> {@code hbm2ddl.auto=update} 가 만드는 테이블은
 * {@code PopulationJpaEntity} 의 {@code @Table(name = "population")} 이므로 Upsert SQL 도 그 이름이어야 한다.
 * As-Is 는 {@code Population} 이었는데, {@code lower_case_table_names=1} 인 Windows/macOS MySQL 에서는
 * 대소문자를 구분하지 않아 드러나지 않고 <b>리눅스(기본 0)에서만 "table doesn't exist" 로 터진다</b>.
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
        INSERT INTO population (sigungu_code, population_count)
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
