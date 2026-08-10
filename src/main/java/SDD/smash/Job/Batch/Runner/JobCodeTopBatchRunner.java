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
<<<<<<< HEAD
=======
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
>>>>>>> origin/Backup/main
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
<<<<<<< HEAD
=======
@ConditionalOnProperty(name = "seed.jobs.job-code-top.enabled", havingValue = "true")
>>>>>>> origin/Backup/main
@Slf4j
public class JobCodeTopBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job jcTopJob;
    private final BatchGuard guard;
<<<<<<< HEAD
=======
    private final SeedProperties seedProperties;
>>>>>>> origin/Backup/main

    private final String SEED_VERSION;

    public JobCodeTopBatchRunner(JobLauncher jobLauncher, @Qualifier("jcTopJob") Job jcTopJob,
                                 BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.jcTopJob = jcTopJob;
        this.guard = guard;
<<<<<<< HEAD
=======
        this.seedProperties = seedProperties;
>>>>>>> origin/Backup/main
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    public void runjcTopJobAfterStartup() throws Exception {
        if(guard.alreadyDone("jcTopJob",SEED_VERSION)){
            log.info("Already jcTopJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                jcTopJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );

    }

}
