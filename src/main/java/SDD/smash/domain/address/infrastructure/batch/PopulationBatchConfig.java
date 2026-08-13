package SDD.smash.domain.address.infrastructure.batch;

import SDD.smash.domain.address.application.PopulationCollectService;
import SDD.smash.domain.address.application.dto.PopulationCollectionInfo;
import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.infrastructure.batch.dto.PopulationUpsertRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;


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

    private static final DateTimeFormatter BASE_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final PopulationCollectService populationCollectService;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;



    @Bean
    public Job PopulationJob(Step populationStep) {
        return new JobBuilder("PopulationJob", jobRepository)
                .start(populationStep)
                .build();
    }

    @Bean
    public Step populationStep(ItemReader<PopulationSnapshot> populationApiReader) {

        return new StepBuilder("populationStep", jobRepository)
                .<PopulationSnapshot, PopulationUpsertRow> chunk(100, platformTransactionManager)
                .reader(populationApiReader)
                .processor(populationApiProcessor())
                .writer(populationWriter())
                .build();
    }

    /**
     * KOSIS 인구 API 를 한 번 호출해 그 달치 시군구 스냅샷을 읽는다.
     *
     * <p>KOSIS 에는 페이지네이션 파라미터가 없다. 대신 "기준월 1개 = 요청 1회" 로 쪼개
     * 메모리에 올라오는 것은 항상 한 달치(시군구 약 250건)뿐이다.
     *
     * <p>인증키가 없거나 해당 월 자료가 없으면 <b>빈 목록</b>이 온다 — Step 은 0건으로 정상 종료하고
     * 기존 인구 데이터는 그대로 보존된다. 빈 키로 API 를 호출하지 않는다.
     */
    @Bean
    @StepScope
    public IteratorItemReader<PopulationSnapshot> populationApiReader(
            @Value("#{jobParameters['baseMonth']}") String baseMonth) {

        YearMonth requested = (baseMonth == null || baseMonth.isBlank())
                ? null
                : YearMonth.parse(baseMonth, BASE_MONTH_FORMAT);

        PopulationCollectionInfo info = populationCollectService.collect(requested);
        return new IteratorItemReader<>(info.snapshots());
    }

    /** 시군구 대조는 수집 단계에서 이미 끝났다. 여기서는 Upsert 파라미터로만 바꾼다. */
    @Bean
    public ItemProcessor<PopulationSnapshot, PopulationUpsertRow> populationApiProcessor() {
        return PopulationSnapshotBatchMapper::toUpsertRow;
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
