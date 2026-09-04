package SDD.smash.domain.infra.infrastructure.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * {@code infraCollectStep} 의 구조화 로그.
 *
 * <p>회차 키와 진척(수집 완료 / 기대 대상)을 함께 남겨, 며칠에 걸친 수집이 어디까지 왔는지를
 * 로그만 보고 알 수 있게 한다. 수집 지표는 Reader 가 들고 있는 것을 그대로 읽는다 —
 * Reader 는 {@code @StepScope} 라 실행마다 새 인스턴스이고, {@code afterStep} 시점에도
 * Step 스코프가 살아 있다.
 *
 * <p>실패 원인은 예외 클래스명과 메시지 첫 줄까지만 남긴다. 스택 전체나 응답 본문은 남기지 않는다.
 */
@Slf4j
public class InfraCollectStepLogger implements StepExecutionListener {

    private final InfraTargetCollectReader reader;

    public InfraCollectStepLogger(InfraTargetCollectReader reader) {
        this.reader = reader;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String baseDate = stepExecution.getJobParameters().getString("baseDate");
        long elapsedMs = InfraStepLogSupport.elapsedMillis(stepExecution);
        boolean failed = stepExecution.getStatus().isUnsuccessful();

        if (failed) {
            log.error("[infraJob] step=infraCollectStep, baseDate={}, {}, staged={}, elapsed={}ms, "
                            + "status={}, reason={}",
                    baseDate, summary(), stepExecution.getWriteCount(), elapsedMs,
                    stepExecution.getStatus(), InfraStepLogSupport.firstFailure(stepExecution));
        } else {
            log.info("[infraJob] step=infraCollectStep, baseDate={}, {}, staged={}, elapsed={}ms, status={}",
                    baseDate, summary(), stepExecution.getWriteCount(), elapsedMs, stepExecution.getStatus());
        }
        return stepExecution.getExitStatus();
    }

    private String summary() {
        try {
            return reader.summary();
        } catch (RuntimeException e) {
            // 수집 경로가 아니어서 Reader 가 만들어지지 않았을 수 있다. 로그가 Step 을 죽이면 안 된다.
            return "collect=skipped";
        }
    }
}
