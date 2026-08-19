package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * 반영에 성공한 회차의 staging 을 지운다. {@code infraStep} 에 붙는다.
 *
 * <h2>언제 지우는가</h2>
 * <ul>
 *   <li>Step 이 <b>성공</b>했고</li>
 *   <li>Reader 가 "이 회차를 반영했다"고 표시({@link #CTX_APPLIED_RUN_KEY})했을 때만</li>
 * </ul>
 * 회차가 미완성이라 Reader 가 건너뛴 실행에서는 이 키가 없으므로 <b>아무것도 지우지 않는다.</b>
 * 지워 버리면 며칠에 걸쳐 모은 수집분이 날아간다.
 *
 * <p>삭제는 카운트 → 대상 진행 순서다. 중간에 끊겨 대상 진행만 남으면 다음 실행이 같은 회차를
 * "완성됐지만 카운트가 없는" 상태로 보고 다시 정리한다(스스로 회복된다). 반대 순서였다면
 * 회차 키를 잃은 카운트 행이 영영 남는다.
 *
 * <p>정리 실패가 배치를 죽이면 안 되므로 예외를 삼키고 경고만 남긴다.
 */
@Slf4j
@RequiredArgsConstructor
public class InfraStagingCleanupListener implements StepExecutionListener {

    /** Reader 가 "이 회차를 반영했다"고 남기는 키. */
    public static final String CTX_APPLIED_RUN_KEY = "infra.staging.appliedRunKey";

    private final InfraCollectionStagingStore stagingStore;

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus().isUnsuccessful()) {
            return stepExecution.getExitStatus();
        }
        if (!stepExecution.getExecutionContext().containsKey(CTX_APPLIED_RUN_KEY)) {
            return stepExecution.getExitStatus();
        }

        String runKey = stepExecution.getExecutionContext().getString(CTX_APPLIED_RUN_KEY);
        try {
            stagingStore.purge(runKey);
        } catch (RuntimeException e) {
            log.warn("[infraJob] staging 정리 실패 runKey={} - 다음 실행이 같은 회차를 다시 정리한다.", runKey, e);
        }
        return stepExecution.getExitStatus();
    }
}
