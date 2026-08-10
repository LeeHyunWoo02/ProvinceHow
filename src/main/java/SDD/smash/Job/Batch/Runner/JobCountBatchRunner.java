package SDD.smash.Job.Batch.Runner;

import SDD.smash.Config.SeedProperties;
import SDD.smash.Util.BatchGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
<<<<<<< HEAD
import org.springframework.batch.core.explore.JobExplorer;
=======
>>>>>>> origin/Backup/main
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
@ConditionalOnProperty(name = "seed.jobs.job-count.enabled", havingValue = "true")
>>>>>>> origin/Backup/main
@Slf4j
public class JobCountBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job jobCountJob;
<<<<<<< HEAD
    private final JobExplorer jobExplorer;
    private final BatchGuard guard;

    private final String SEED_VERSION;

    public JobCountBatchRunner(JobLauncher jobLauncher, @Qualifier("jobCountJob") Job jobCountJob, JobExplorer jobExplorer,
                               BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.jobCountJob = jobCountJob;
        this.jobExplorer = jobExplorer;
        this.guard = guard;
=======
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public JobCountBatchRunner(JobLauncher jobLauncher, @Qualifier("jobCountJob") Job jobCountJob,
                               BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.jobCountJob = jobCountJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
>>>>>>> origin/Backup/main
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @EventListener(ApplicationReadyEvent.class)
<<<<<<< HEAD
    @Order(8)
    public void runjobCountJobAfterStartup() throws Exception {
        if (!jobExplorer.findRunningJobExecutions("jobCountJob").isEmpty()) {
            log.warn("jobCountJob is already running. Skip launching.");
            return;
        }

=======
    @Order(9)
    public void runjobCountJobAfterStartup() throws Exception {
>>>>>>> origin/Backup/main
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
