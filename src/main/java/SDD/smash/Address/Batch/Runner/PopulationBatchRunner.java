package SDD.smash.Address.Batch.Runner;

import SDD.smash.Config.SeedProperties;
import SDD.smash.Util.BatchGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
