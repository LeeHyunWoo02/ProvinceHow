package SDD.smash.address.infrastructure.batch.runner;

import SDD.smash.Util.BatchGuard;
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
 * 시드 배치 실행 순서 2번. Sido 적재 이후에 돈다.
 * {@code @Order} 값은 FK 선후관계를 통제하므로 바꾸지 않는다.
 */
@Component
@ConditionalOnProperty(name = "seed.jobs.sigungu.enabled", havingValue = "true")
@Slf4j
public class SigunguBatchRunner {
    private final JobLauncher jobLauncher;
    private final Job SigunguJob;
    private final BatchGuard guard;
    private final SeedProperties seedProperties;

    private final String SEED_VERSION;

    public SigunguBatchRunner(JobLauncher jobLauncher, @Qualifier("SigunguJob") Job sigunguJob,
                              BatchGuard guard, SeedProperties seedProperties) {
        this.jobLauncher = jobLauncher;
        SigunguJob = sigunguJob;
        this.guard = guard;
        this.seedProperties = seedProperties;
        this.SEED_VERSION = seedProperties.getVersion();
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void runSigunguJobAfterStartup() throws Exception{
        if(guard.alreadyDone("SigunguJob",SEED_VERSION)){
            log.info("Already SigunguJob : " + SEED_VERSION );
            return;
        }

        jobLauncher.run(
                SigunguJob,
                new JobParametersBuilder()
                        .addString("seedVersion", SEED_VERSION)
                        .toJobParameters()
        );
    }
}
