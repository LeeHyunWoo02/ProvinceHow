package SDD.smash.infra.infrastructure.batch.runner;

import SDD.smash.Util.BatchGuard;
import SDD.smash.common.config.SeedProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 시드 배치 실행 순서 6번. 선행 의존 없음.
 * {@code @Order} 값은 적재 선후관계를 통제하므로 바꾸지 않는다.
 */
@Component
@ConditionalOnProperty(name = "seed.jobs.industry.enabled", havingValue = "true")
@Slf4j
public class IndustryBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job industryJob;
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public IndustryBatchRunner(JobLauncher jobLauncher, @Qualifier("industryJob") Job industryJob,
                               BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.industryJob = industryJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @Order(6)
    @EventListener(ApplicationReadyEvent.class)
    public void runIndustryJobAfterStartup() throws Exception {
        if(guard.alreadyDone("industryJob",SEED_VERSION)){
            log.info("Already industryJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                industryJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );

    }
}
