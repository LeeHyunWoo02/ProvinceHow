package SDD.smash.dwelling.infrastructure.batch;

import SDD.smash.common.batch.BatchGuard;
import SDD.smash.common.config.SeedProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 전월세 배치 실행기. As-Is {@code DwellingBatchRunner} 를 옮긴 것이다.
 *
 * <p><b>{@code @Order} 는 As-Is 값 9 를 그대로 유지한다.</b>
 * architecture-conventions §6.2 표는 Dwelling 을 10 으로 적고 있으나 실제 코드는 9 이며,
 * 이관에서 값을 바꾸는 것은 실행 순서 변경에 해당하므로 손대지 않았다.
 * 현재 가장 큰 값이라 실행 순서상 마지막인 것은 표와 동일하다.
 *
 * <p>시드 러너들과 달리 {@code @ConditionalOnProperty} 가 없어 기동할 때마다 돈다.
 * 테스트에서는 {@code IntegrationTestSupport} 가 이 빈을 대역으로 바꿔 외부 API 호출을 막는다.
 */
@Component
@Slf4j
public class DwellingBatchRunner {

    private final Job dwellingJob;
    private final JobLauncher jobLauncher;
    private final BatchGuard guard;

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
    @EventListener(ApplicationReadyEvent.class)
    public void runOnceAfterStartup() throws Exception {
        try {

            if (guard.alreadyDone("dwellingJob", SEED_VERSION)) {
                log.info("Already dwellingJob : " + SEED_VERSION);
                return;
            }

            long months = 12L;

            JobParameters params = new JobParametersBuilder()
                    .addString("dealYmd", DEALYMD)
                    .addLong("months", months)
                    .addString("seedVersion", SEED_VERSION)
                    .addLong("triggerTime", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution exec = jobLauncher.run(dwellingJob, params);
            log.info("dwellingJob started: id={}, params={}", exec.getId(), params);
        } catch (Exception e) {
            log.error("Failed to run dwellingJob: {}", e.getMessage(), e);
        }
    }
}
