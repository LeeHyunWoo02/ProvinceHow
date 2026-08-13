package SDD.smash.domain.dwelling.infrastructure.batch;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.domain.dwelling.domain.port.RentRecordProvider;
import SDD.smash.domain.dwelling.domain.service.RentStatCalculator;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.DwellingUpsertRow;
import SDD.smash.domain.dwelling.infrastructure.batch.dto.WorkItem;
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
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static SDD.smash.global.exception.ErrorCode.NOT_FOUND_YEARMONTH;

/**
 * 전월세 적재 배치. As-Is {@code DwellingBatch} 를 옮긴 것이다.
 *
 * <p>Job 이름("dwellingJob")과 빈 이름, chunk 크기, 재시도 설정, Upsert SQL 을 그대로 유지한다.
 * Step 빈 이름("dwellingStep")은 {@code seedMasterJob} 이 {@code @Qualifier} 로 찾고
 * {@code BatchGuard} 가 STEP_NAME 으로 재실행 여부를 판단하므로 바꾸지 않는다.
 *
 * <p>바뀐 것은 협력자뿐이다.
 * 시군구 목록은 address 의 application Service 로, 외부 API 호출은 {@code RentRecordProvider} 포트로,
 * 평균·중앙값 계산은 {@code RentStatCalculator} 도메인 서비스로 간다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class DwellingBatchConfig {

    /** {@code months} 파라미터가 없을 때 집계할 개월 수. As-Is Runner 가 넘기던 값과 같다. */
    private static final long DEFAULT_AGGREGATION_MONTHS = 12L;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final RentRecordProvider rentRecordProvider;
    private final AddressQueryService addressQueryService;
    private final DwellingScoreCacheCleaner dwellingScoreCacheCleaner;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    @Bean
    public Job dwellingJob(Step dwellingStep) {
        return new JobBuilder("dwellingJob", jobRepository)
                .listener(dwellingScoreCacheCleaner)
                .start(dwellingStep)
                .build();
    }

    @Bean
    public Step dwellingStep(ItemReader<WorkItem> dwellingReader,
                             ItemProcessor<WorkItem, DwellingUpsertRow> dwellingProcessor,
                             JdbcBatchItemWriter<DwellingUpsertRow> dwellingWriterJdbc) {
        return new StepBuilder("dwellingStep", jobRepository)
                .<WorkItem, DwellingUpsertRow>chunk(10, platformTransactionManager)
                .reader(dwellingReader)
                .processor(dwellingProcessor)
                .writer(dwellingWriterJdbc)
                .faultTolerant()
                .retry(org.springframework.web.client.ResourceAccessException.class) // Read timed out 포함
                .retry(java.net.SocketTimeoutException.class)
                .retryLimit(3)
                .backOffPolicy(new FixedBackOffPolicy() {{ setBackOffPeriod(1000L); }})
                .build();
    }

    /**
     * READER — 전 시군구를 읽어 WorkItem(시군구 + from~to 기간)으로 만든다.
     *
     * <p>기준월 파라미터는 {@code baseMonth}(yyyyMM)다. 월 단위로 갱신되는 데이터의 재실행 판정 기준이
     * 기준월이므로 As-Is 의 {@code dealYmd} 대신 이 이름을 쓴다.
     * {@code months} 는 집계 구간 길이이며 없으면 12개월로 본다(재실행 판정에 쓰이지 않는다).
     */
    @Bean
    @StepScope
    public IteratorItemReader<WorkItem> dwellingReader(
            @Value("#{jobParameters['baseMonth']}") String baseMonth,
            @Value("#{jobParameters['months']}") Long months
    ) {
        if (baseMonth == null || baseMonth.isBlank()) {
            throw new DomainException(NOT_FOUND_YEARMONTH, "baseMonth is null");
        }
        long aggregationMonths = (months == null) ? DEFAULT_AGGREGATION_MONTHS : months;
        YearMonth to = YearMonth.parse(baseMonth, DateTimeFormatter.ofPattern("yyyyMM"));
        YearMonth from = to.minusMonths(aggregationMonths - 1);

        List<WorkItem> items = addressQueryService.getAllSigunguCodes().stream()
                .map(code -> new WorkItem(code, from, to))
                .toList();

        return new IteratorItemReader<>(items);
    }

    /**
     * PROCESSOR
     * - 시군구별로 월 단위 API 호출 → 월세/전세 분리 → 평균·중앙값 계산
     * - 월세는 월세금액 &gt; 0, 전세는 월세금액 == 0 인 건의 보증금이다(As-Is 판정 그대로)
     * - 양쪽 다 비어 있으면 null 반환(= writer 로 넘기지 않음)
     */
    @Bean
    public ItemProcessor<WorkItem, DwellingUpsertRow> dwellingProcessor() {
        return work -> {
            SigunguCode sigunguCode = work.sigunguCode();

            List<RentRecord> all = new ArrayList<>();
            for (YearMonth ym = work.from(); !ym.isAfter(work.to()); ym = ym.plusMonths(1)) {
                List<RentRecord> records = rentRecordProvider.fetch(sigunguCode, ym);
                if (records.isEmpty()) {
                    log.warn("No records for sigungu={}, ym={}", sigunguCode.value(), ym);
                }
                all.addAll(records);
            }

            List<Integer> monthValues = all.stream()
                    .filter(RentRecord::isMonthly)
                    .map(RentRecord::monthlyRent)
                    .toList();

            List<Integer> jeonseValues = all.stream()
                    .filter(RentRecord::isJeonse)
                    .map(RentRecord::deposit)
                    .toList();

            if (monthValues.isEmpty() && jeonseValues.isEmpty()) {
                log.warn("Skip: aggregated empty for sigungu={}", sigunguCode.value());
                return null; // skip
            }

            return DwellingUpsertRow.builder()
                    .sigunguCode(sigunguCode.value())
                    .monthAvg(RentStatCalculator.mean(monthValues))
                    .monthMid(RentStatCalculator.median(monthValues))
                    .jeonseAvg(RentStatCalculator.mean(jeonseValues))
                    .jeonseMid(RentStatCalculator.median(jeonseValues))
                    .build();
        };
    }

    /**
     * <b>테이블명은 {@code dwelling}(소문자)이다.</b> {@code hbm2ddl.auto=update} 가 만드는 테이블은
     * {@code DwellingJpaEntity} 의 {@code @Table(name = "dwelling")} 이다.
     * As-Is 의 {@code Dwelling} 은 {@code lower_case_table_names=1} 인 Windows/macOS MySQL 에서만
     * 우연히 통과했고 리눅스(기본 0)에서는 존재하지 않는 테이블이다.
     */
    @Bean
    public JdbcBatchItemWriter<DwellingUpsertRow> dwellingWriterJdbc() {
        final String upsertSql = """
        INSERT INTO dwelling (sigungu_code, month_avg, month_mid, jeonse_avg, jeonse_mid)
        VALUES (:sigunguCode, :monthAvg, :monthMid, :jeonseAvg, :jeonseMid)
        ON DUPLICATE KEY UPDATE
            month_avg  = VALUES(month_avg),
            month_mid  = VALUES(month_mid),
            jeonse_avg = VALUES(jeonse_avg),
            jeonse_mid = VALUES(jeonse_mid)
        """;
        return new JdbcBatchItemWriterBuilder<DwellingUpsertRow>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }
}
