package SDD.smash.global.batch.listener;

import SDD.smash.global.batch.seed.SeedStepSpec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 모든 Step 에 공통으로 붙는 구조화 로그.
 * 배치 이름 / 기준일·기준월 / 읽은 건수 / 필터링 건수 / 저장 건수 / 소요 시간 / 최종 상태 / 실패 원인을 남긴다.
 *
 * <p><b>JobParameters 를 통째로 찍지 않는다.</b> 파라미터에는 앞으로 API 키가 섞일 수 있으므로
 * 이 리스너가 아는 기준 파라미터({@code seedVersion} / {@code baseDate} / {@code baseMonth})만 꺼내 쓴다.
 * 외부 API URL 이나 serviceKey 는 어떤 경우에도 로그에 남기지 않는다.
 */
@Slf4j
public class SeedStepExecutionListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("[batch] step={} job={} 시작 {}",
                stepExecution.getStepName(),
                stepExecution.getJobExecution().getJobInstance().getJobName(),
                baseline(stepExecution.getJobParameters()));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        log.info("[batch] step={} job={} {} read={} filter={} write={} skip={} commit={} elapsedMs={} status={} exit={}",
                stepExecution.getStepName(),
                stepExecution.getJobExecution().getJobInstance().getJobName(),
                baseline(stepExecution.getJobParameters()),
                stepExecution.getReadCount(),
                stepExecution.getFilterCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getCommitCount(),
                elapsedMillis(stepExecution),
                stepExecution.getStatus(),
                stepExecution.getExitStatus().getExitCode());

        stepExecution.getFailureExceptions().forEach(failure ->
                log.error("[batch] step={} 실패 원인", stepExecution.getStepName(), failure));

        return null;    // ExitStatus 를 바꾸지 않는다
    }

    private String baseline(JobParameters jobParameters) {
        return "seedVersion=%s baseDate=%s baseMonth=%s".formatted(
                jobParameters.getString(SeedStepSpec.SEED_VERSION),
                jobParameters.getString(SeedStepSpec.BASE_DATE),
                jobParameters.getString(SeedStepSpec.BASE_MONTH));
    }

    private long elapsedMillis(StepExecution stepExecution) {
        LocalDateTime startTime = stepExecution.getStartTime();
        LocalDateTime endTime = stepExecution.getEndTime();
        if (startTime == null) {
            return 0L;
        }
        return Duration.between(startTime, endTime == null ? LocalDateTime.now() : endTime).toMillis();
    }
}
