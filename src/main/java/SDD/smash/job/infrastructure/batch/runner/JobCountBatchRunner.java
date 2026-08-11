package SDD.smash.job.infrastructure.batch.runner;

import SDD.smash.Util.BatchGuard;
import SDD.smash.common.config.SeedProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 시드 배치 실행 순서 <b>8번</b>. Sigungu 와 JobCodeMiddle 적재 이후에 돈다.
 *
 * <p>{@code @Order} 값은 적재 선후관계를 통제하므로 바꾸지 않는다.
 * (architecture-conventions §6.2 표의 실측값과 같다 — Dwelling 이 9 로 그 뒤를 잇는다.)
 */
@Component
@ConditionalOnProperty(name = "seed.jobs.job-count.enabled", havingValue = "true")
@Slf4j
public class JobCountBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job jobCountJob;
    private final JobExplorer jobExplorer;
    private final BatchGuard guard;

    private final String SEED_VERSION;

    public JobCountBatchRunner(JobLauncher jobLauncher, @Qualifier("jobCountJob") Job jobCountJob, JobExplorer jobExplorer,
                               BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        this.jobCountJob = jobCountJob;
        this.jobExplorer = jobExplorer;
        this.guard = guard;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(8)
    public void runjobCountJobAfterStartup() throws Exception {
        if (!jobExplorer.findRunningJobExecutions("jobCountJob").isEmpty()) {
            log.warn("jobCountJob is already running. Skip launching.");
            return;
        }

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
