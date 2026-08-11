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
import org.springframework.stereotype.Component;

/**
 * 시드 배치 실행 순서 5번. Sigungu 적재 이후에 돈다.
 * {@code @Order} 값은 FK 선후관계를 통제하므로 바꾸지 않는다.
 */
@Component
@ConditionalOnProperty(name = "seed.jobs.population.enabled", havingValue = "true")
@Slf4j
public class PopulationBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job PopulationJob;
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public PopulationBatchRunner(JobLauncher jobLauncher, @Qualifier("PopulationJob") Job populationJob,
                                 BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        PopulationJob = populationJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void runPopulationJobAfterStartup() throws Exception{
        if(guard.alreadyDone("PopulationJob",SEED_VERSION)){
            log.info("Already PopulationJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                PopulationJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );
    }
}
