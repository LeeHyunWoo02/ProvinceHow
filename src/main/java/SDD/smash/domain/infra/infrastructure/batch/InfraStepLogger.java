package SDD.smash.domain.infra.infrastructure.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * {@code infraStep} 의 구조화 로그.
 *
 * <p>배치 이름 / 기준일 / 수집 지표(호출 수·읽은 건수·필터링·매핑 실패) / 저장 건수 /
 * 소요 시간 / 최종 상태 / 실패 원인을 한 줄로 남긴다. 수집 지표는 Reader 가
 * Step ExecutionContext 에 넣어 둔 것을 읽는다.
 *
 * <p>실패 원인은 예외 클래스명과 메시지 첫 줄까지만 남긴다 — 스택 전체나 응답 본문은 남기지 않는다.
 */
@Slf4j
public class InfraStepLogger implements StepExecutionListener {

    /** Reader 가 수집 지표를 넣어 두는 키. */
    public static final String CTX_SUMMARY = "infra.collect.summary";

    private static final String NOT_COLLECTED = "collect=skipped";

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String baseDate = stepExecution.getJobParameters().getString("baseDate");
        String summary = stepExecution.getExecutionContext().containsKey(CTX_SUMMARY)
                ? stepExecution.getExecutionContext().getString(CTX_SUMMARY)
                : NOT_COLLECTED;

        long elapsedMs = InfraStepLogSupport.elapsedMillis(stepExecution);
        boolean failed = stepExecution.getStatus().isUnsuccessful();

        if (failed) {
            log.error("[infraJob] step=infraStep, baseDate={}, {}, saved={}, filteredByProcessor={}, "
                            + "elapsed={}ms, status={}, reason={}",
                    baseDate, summary, stepExecution.getWriteCount(), stepExecution.getFilterCount(),
                    elapsedMs, stepExecution.getStatus(), InfraStepLogSupport.firstFailure(stepExecution));
        } else {
            log.info("[infraJob] step=infraStep, baseDate={}, {}, saved={}, filteredByProcessor={}, "
                            + "elapsed={}ms, status={}",
                    baseDate, summary, stepExecution.getWriteCount(), stepExecution.getFilterCount(),
                    elapsedMs, stepExecution.getStatus());
        }
        return stepExecution.getExitStatus();
    }
}
