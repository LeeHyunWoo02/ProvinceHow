package SDD.smash.Infra.Batch.Runner;

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
