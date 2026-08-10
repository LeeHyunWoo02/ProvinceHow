package SDD.smash.Dwelling.Batch;

<<<<<<< HEAD
=======
import SDD.smash.Address.Entity.Sigungu;
>>>>>>> origin/Backup/main
import SDD.smash.Address.Repository.SigunguRepository;
import SDD.smash.Dwelling.Adapter.MolitAptRentAdapter;
import SDD.smash.Dwelling.Dto.DwellingDTO;
import SDD.smash.Dwelling.Dto.DwellingUpsertDTO;
import SDD.smash.Dwelling.Dto.RentRecord;
<<<<<<< HEAD
import SDD.smash.Exception.Exception.BusinessException;
=======
import SDD.smash.Dwelling.Entity.Dwelling;
import SDD.smash.Dwelling.Repository.DwellingRepository;
import SDD.smash.Exception.Exception.BusinessException;
import SDD.smash.Infra.Dto.InfraScoreDTO;
import SDD.smash.Infra.Dto.InfraScoreUpsertDTO;
import jakarta.persistence.EntityManagerFactory;
>>>>>>> origin/Backup/main
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
<<<<<<< HEAD
=======
import org.springframework.batch.item.database.JpaItemWriter;
>>>>>>> origin/Backup/main
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

import static SDD.smash.Dwelling.Converter.DwellingConverter.toDTO;
import static SDD.smash.Exception.Code.ErrorCode.NOT_FOUND_YEARMONTH;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class DwellingBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final MolitAptRentAdapter adapter;
    private final SigunguRepository sigunguRepository;
<<<<<<< HEAD
    private final DwellingCacheCleaner dwellingCacheCleaner;
=======
>>>>>>> origin/Backup/main
    private final @Qualifier("dataDBSource") DataSource dataDataSource;

    @Bean
    public Job dwellingJob(Step dwellingStep) {
        return new JobBuilder("dwellingJob", jobRepository)
<<<<<<< HEAD
                .listener(dwellingCacheCleaner)
=======
>>>>>>> origin/Backup/main
                .start(dwellingStep)
                .build();
    }

    /** Step: WorkItem → Dwelling 엔티티 */
    @Bean
    public Step dwellingStep(ItemReader<WorkItem> dwellingReader,
                             ItemProcessor<WorkItem, DwellingUpsertDTO> dwellingProcessor,
                             JdbcBatchItemWriter<DwellingUpsertDTO> dwellingWriterJdbc) {
        return new StepBuilder("dwellingStep", jobRepository)
                .<WorkItem, DwellingUpsertDTO>chunk(10, platformTransactionManager)
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
     * READER
     * - 시군구 전체 목록을 읽어 WorkItem(시군구 코드 + from~to 기간) 리스트로 변환
     */
    @Bean
    @StepScope
<<<<<<< HEAD
    public IteratorItemReader<WorkItem> dwellingReader(
=======
    public ItemReader<WorkItem> dwellingReader(
>>>>>>> origin/Backup/main
            @Value("#{jobParameters['dealYmd']}") String dealYmd,
            @Value("#{jobParameters['months']}") Long months
    ) {
        if (dealYmd == null || months == null) {
            throw new BusinessException(NOT_FOUND_YEARMONTH,"dealYmd or months is null");
        }
        YearMonth to = YearMonth.parse(dealYmd, DateTimeFormatter.ofPattern("yyyyMM"));
        YearMonth from = to.minusMonths(months - 1);

        List<WorkItem> items = sigunguRepository.findAll().stream()
                .map(s -> new WorkItem(s.getSigunguCode(), from, to))
                .toList();

        return new IteratorItemReader<>(items);
    }

    /**
     * PROCESSOR
     * - WorkItem별로 월 단위 API 호출(fetchMonth) → 필요한 필드만 추출(이미 adapter가 RentRecord 리스트로 반환)
     * - 월세/전세 데이터 분리 → 평균/중앙값 계산 → 기존 엔티티 조회/병합하여 Dwelling 리턴
     * - 비어있으면 null 반환(= writer로 안 넘어감)
     */
    @Bean
    public ItemProcessor<WorkItem, DwellingUpsertDTO> dwellingProcessor() {
        return work -> {
            List<RentRecord> all = new ArrayList<>();
            for (YearMonth ym = work.from(); !ym.isAfter(work.to()); ym = ym.plusMonths(1)) {
                List<RentRecord> records = adapter.fetchMonth(work.sigunguCode(), ym, 1, 1000);
                if (records.isEmpty()) {
                    log.warn("No records for sigungu={}, ym={}", work.sigunguCode(), ym);
                }
<<<<<<< HEAD
=======
                log.info("sigungu = {}, ym = {}", work.sigunguCode(), ym);
>>>>>>> origin/Backup/main
                all.addAll(records);
            }

            // 월세 값(>0), 전세 값(보증금, monthlyRent==0)
            List<Integer> monthValues = all.stream()
                    .map(RentRecord::getMonthlyRent)
                    .filter(v -> v != null && v > 0)
                    .toList();

            List<Integer> jeonseValues = all.stream()
                    .filter(r -> r.getMonthlyRent() != null && r.getMonthlyRent() == 0)
                    .map(RentRecord::getDeposit)
                    .toList();

            if (monthValues.isEmpty() && jeonseValues.isEmpty()) {
                log.warn("Skip: aggregated empty for sigungu={}", work.sigunguCode());
                return null; // skip
            }


            DwellingDTO dwellingDTO = toDTO(monthValues, jeonseValues, work.sigunguCode());

            return DwellingUpsertDTO.builder()
                    .sigunguCode(dwellingDTO.getSigunguCode())
                    .jeonseMid(dwellingDTO.getJeonseMid())
                    .jeonseAvg(dwellingDTO.getJeonseAvg())
                    .monthAvg(dwellingDTO.getMonthAvg())
                    .monthMid(dwellingDTO.getMonthMid())
                    .build();
        };
    }

    @Bean
    public JdbcBatchItemWriter<DwellingUpsertDTO> dwellingWriterJdbc() {
        final String upsertSql = """
        INSERT INTO Dwelling (sigungu_code, month_avg, month_mid, jeonse_avg, jeonse_mid)
        VALUES (:sigunguCode, :monthAvg, :monthMid, :jeonseAvg, :jeonseMid)
        ON DUPLICATE KEY UPDATE
            month_avg  = VALUES(month_avg),
            month_mid  = VALUES(month_mid),
            jeonse_avg = VALUES(jeonse_avg),
            jeonse_mid = VALUES(jeonse_mid)
        """;
        return new JdbcBatchItemWriterBuilder<DwellingUpsertDTO>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
<<<<<<< HEAD
=======
                // BeanPropertyItemSqlParameterSourceProvider 는 중첩 프로퍼티를 지원합니다.
                // (:sigungu.sigunguCode 같이 dot path OK)
>>>>>>> origin/Backup/main
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }

}
