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
<<<<<<< HEAD
=======
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
>>>>>>> origin/Backup/main
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
<<<<<<< HEAD
=======
@ConditionalOnProperty(name = "seed.jobs.sido.enabled", havingValue = "true")
>>>>>>> origin/Backup/main
@Slf4j
public class SidoBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job SidoJob;
    private final BatchGuard guard;
<<<<<<< HEAD
=======
    private final SeedProperties seedProperties;
>>>>>>> origin/Backup/main

    private final String SEED_VERSION;

    public SidoBatchRunner(JobLauncher jobLauncher, @Qualifier("SidoJob") Job sidoJob,
                           BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        SidoJob = sidoJob;
        this.guard = guard;
<<<<<<< HEAD
=======
        this.seedProperties = seedProperties;
>>>>>>> origin/Backup/main
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
