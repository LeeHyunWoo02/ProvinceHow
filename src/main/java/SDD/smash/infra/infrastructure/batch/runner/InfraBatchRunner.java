package SDD.smash.infra.infrastructure.batch.runner;

import SDD.smash.common.batch.BatchGuard;
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
 * 시드 배치 실행 순서 7번. Sigungu 와 Industry 적재 이후에 돈다.
 * {@code @Order} 값은 적재 선후관계를 통제하므로 바꾸지 않는다.
 */
@Component
@ConditionalOnProperty(name = "seed.jobs.infra.enabled", havingValue = "true")
@Slf4j
public class InfraBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job infraJob;
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public InfraBatchRunner(JobLauncher jobLauncher, @Qualifier("infraJob") Job infraJob,
                            BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.infraJob = infraJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @Order(7)
    @EventListener(ApplicationReadyEvent.class)
    public void runInfraAfterStartup() throws Exception {
        if(guard.alreadyDone("infraJob",SEED_VERSION)){
            log.info("Already infraJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                infraJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );

    }
}
