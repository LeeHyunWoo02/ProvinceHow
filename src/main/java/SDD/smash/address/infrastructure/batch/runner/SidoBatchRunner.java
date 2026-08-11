package SDD.smash.address.infrastructure.batch.runner;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 시드 배치 실행 순서 1번. 선행 의존 없음.
 * {@code @Order} 값은 FK 선후관계를 통제하므로 바꾸지 않는다.
 */
@Component
@ConditionalOnProperty(name = "seed.jobs.sido.enabled", havingValue = "true")
@Slf4j
public class SidoBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job SidoJob;
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public SidoBatchRunner(JobLauncher jobLauncher, @Qualifier("SidoJob") Job sidoJob,
                           BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        SidoJob = sidoJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @Order(1)
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void runSidoJobAfterStartup() throws Exception {
        if(guard.alreadyDone("SidoJob",SEED_VERSION)){
            log.info("Already SidoJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                SidoJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );

    }
}
