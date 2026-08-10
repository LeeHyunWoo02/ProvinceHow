package SDD.smash.Job.Batch.Runner;

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
@ConditionalOnProperty(name = "seed.jobs.job-count.enabled", havingValue = "true")
@Slf4j
public class JobCountBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job jobCountJob;
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public JobCountBatchRunner(JobLauncher jobLauncher, @Qualifier("jobCountJob") Job jobCountJob,
                               BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.jobCountJob = jobCountJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(9)
    public void runjobCountJobAfterStartup() throws Exception {
        if(guard.alreadyDone("jobCountJob",SEED_VERSION)){
            log.info("Already jobCountJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                jobCountJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );

    }

}
