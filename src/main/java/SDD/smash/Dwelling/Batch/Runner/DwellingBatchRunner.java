package SDD.smash.Dwelling.Batch.Runner;

import SDD.smash.Config.SeedProperties;
<<<<<<< HEAD
import SDD.smash.Util.BatchGuard;
=======
import SDD.smash.Exception.Exception.BusinessException;
import SDD.smash.Util.BatchGuard;
import lombok.RequiredArgsConstructor;
>>>>>>> origin/Backup/main
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
<<<<<<< HEAD
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
=======
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
>>>>>>> origin/Backup/main
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

<<<<<<< HEAD
=======
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
>>>>>>> origin/Backup/main

@Component
@Slf4j
public class DwellingBatchRunner {
    private final Job dwellingJob;
    private final JobLauncher jobLauncher;
    private final BatchGuard guard;
<<<<<<< HEAD

    private final String SEED_VERSION;
    private final String DEALYMD;

    public DwellingBatchRunner(@Qualifier("dwellingJob") Job dwellingJob, JobLauncher jobLauncher,
                               BatchGuard guard, SeedProperties seedProperties,
                               @Value("${dwelling.dealYmd}") String dealymd) {
        this.dwellingJob = dwellingJob;
        this.jobLauncher = jobLauncher;
        this.guard = guard;
        this.SEED_VERSION = seedProperties.getVersion();
        this.DEALYMD = dealymd;
    }

    @Order(9)
=======
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public DwellingBatchRunner(@Qualifier("dwellingJob") Job dwellingJob, JobLauncher jobLauncher,
                               BatchGuard guard, SeedProperties seedProperties) {
        this.dwellingJob = dwellingJob;
        this.jobLauncher = jobLauncher;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @Order(10)
>>>>>>> origin/Backup/main
    @EventListener(ApplicationReadyEvent.class)
    public void runOnceAfterStartup() throws Exception {
        try{

            if(guard.alreadyDone("dwellingJob",SEED_VERSION)){
                log.info("Already dwellingJob : " + SEED_VERSION );
                return;
            }

<<<<<<< HEAD
=======
            String dealYmd = "202509";
>>>>>>> origin/Backup/main
            long months = 12L;
            String codes = null;

            JobParameters params = new JobParametersBuilder()
<<<<<<< HEAD
                    .addString("dealYmd", DEALYMD)
=======
                    .addString("dealYmd", dealYmd)
>>>>>>> origin/Backup/main
                    .addLong("months", months)
                    .addString("seedVersion", SEED_VERSION)
                    .addLong("triggerTime", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution exec = jobLauncher.run(dwellingJob, params);
            log.info("dwellingJob started: id={}, params={}", exec.getId(), params);
        } catch (Exception e){
            log.error("Failed to run dwellingJob: {}", e.getMessage(), e);
        }
    }
}
