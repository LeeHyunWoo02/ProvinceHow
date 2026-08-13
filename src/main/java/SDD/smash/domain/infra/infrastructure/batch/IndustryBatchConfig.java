package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaEntity;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaRepository;
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
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * 업종 마스터 시드 배치.
 *
 * <h2>원천이 CSV 에서 설정 파일로 바뀌었다</h2>
 * As-Is 는 {@code data/industry.csv} 를 읽었는데 <b>그 파일이 저장소에 없었다.</b>
 * 그래서 {@code industry} 테이블이 비고, 그러면 {@code infra} 조회 3개가 전부
 * {@code JOIN IndustryJpaEntity} 에서 걸러져 인프라 기능 전체가 죽는다.
 *
 * <p>이제 {@code src/main/resources/infra/industry-master.yml}(위치는 프로퍼티로 교체 가능)이
 * 정본이다. 업종 코드·이름·대분류·엔드포인트 slug 를 한 파일에서 관리하고,
 * 인프라 수집 배치도 같은 파일을 쓴다 — 두 배치가 같은 업종 목록을 보게 하기 위해서다.
 *
 * <h2>대분류(Major)는 자동 추론하지 않는다</h2>
 * 어느 업종이 어느 대분류인지는 <b>서비스 기획 판단</b>이다. 마스터에 {@code major} 가
 * 비어 있는 항목은 적재하지 않고 로그로만 남긴다. 값이 잘못된 문자열이어도
 * (As-Is 의 {@code Major.valueOf} 처럼) 배치 전체를 죽이지 않는다.
 *
 * <p>Job 이름("industryJob")과 Step 빈 이름("industryStep")은 바꾸지 않는다 —
 * {@code SeedMasterJobConfig} 가 {@code @Qualifier} 로, {@code DataRefreshScheduler} 가
 * Job 이름으로 참조한다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class IndustryBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final IndustryJpaRepository industryJpaRepository;
    private final InfraMasterCatalog masterCatalog;

    @Bean
    public Job industryJob(Step industryStep) {
        return new JobBuilder("industryJob", jobRepository)
                .start(industryStep)
                .build();
    }

    @Bean
    public Step industryStep(ItemReader<IndustryMasterEntry> industryMasterReader,
                             ItemProcessor<IndustryMasterEntry, IndustryJpaEntity> industryMasterProcessor,
                             RepositoryItemWriter<IndustryJpaEntity> industryWriter) {
        return new StepBuilder("industryStep", jobRepository)
                .<IndustryMasterEntry, IndustryJpaEntity>chunk(20, platformTransactionManager)
                .reader(industryMasterReader)
                .processor(industryMasterProcessor)
                .writer(industryWriter)
                .build();
    }

    /**
     * READER — 업종 마스터에서 적재 가능한 항목만 읽는다.
     *
     * @param baseDate 기준일 JobParameter. 재실행 판정과 로그에 쓰인다
     */
    @Bean
    @StepScope
    public ItemReader<IndustryMasterEntry> industryMasterReader(
            @Value("#{jobParameters['baseDate']}") String baseDate) {

        List<IndustryMasterEntry> active = masterCatalog.industryMaster().active();
        List<IndustryMasterEntry> needsReview = masterCatalog.industryMaster().needingReview();

        log.info("[industryJob] baseDate={}, total={}, active={}, needsReview={}",
                baseDate, masterCatalog.industryMaster().entries().size(), active.size(), needsReview.size());
        if (!needsReview.isEmpty()) {
            // 대분류가 확정된 항목은 "제안 상태"라도 적재한다 — 그래야 기능이 동작하고 검토 대상이 눈에 보인다.
            // 대분류가 null 인 항목은 active() 가 이미 제외했다.
            log.warn("[industryJob] 대분류(Major) 확인이 필요한 업종 {}건 codes={}",
                    needsReview.size(), needsReview.stream().map(entry -> entry.code().value()).toList());
        }
        if (active.isEmpty()) {
            log.warn("[industryJob] 적재할 업종이 없다. infra/industry-master.yml 의 major/enabled 를 확인하라.");
        }
        return new IteratorItemReader<>(active);
    }

    @Bean
    public ItemProcessor<IndustryMasterEntry, IndustryJpaEntity> industryMasterProcessor() {
        return InfraCsvMapper::toIndustryJpaEntity;
    }

    /**
     * WRITER — {@code save} 는 PK({@code industry_code})가 이미 있으면 merge 라 멱등하다.
     * 같은 기준일에 다시 돌려도 결과가 같다.
     */
    @Bean
    public RepositoryItemWriter<IndustryJpaEntity> industryWriter() {
        return new RepositoryItemWriterBuilder<IndustryJpaEntity>()
                .repository(industryJpaRepository)
                .methodName("save")
                .build();
    }
}
