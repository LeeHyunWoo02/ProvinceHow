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
@ConditionalOnProperty(name = "seed.jobs.job-code-middle.enabled", havingValue = "true")
@Slf4j
public class JobCodeMiddleBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job jcMiddleJob;
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public JobCodeMiddleBatchRunner(JobLauncher jobLauncher,@Qualifier("jcMiddleJob") Job jcMiddleJob,
                                    BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.jcMiddleJob = jcMiddleJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(4)
    public void runjcToMiddleJobAfterStartup() throws Exception {
        if(guard.alreadyDone("jcMiddleJob",SEED_VERSION)){
            log.info("Already jcMiddleJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                jcMiddleJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );

    }

}
