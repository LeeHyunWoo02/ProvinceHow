package SDD.smash.domain.infra.infrastructure.batch;

import org.springframework.batch.core.StepExecution;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * infra 배치 Step 로거들이 공유하는 로그 조립 헬퍼. 소요 시간·실패 원인 요약을 한곳에 둔다.
 */
final class InfraStepLogSupport {

    private InfraStepLogSupport() {
    }

    /** Step 시작~종료 소요 밀리초. 아직 끝나지 않았으면 현재 시각까지로 잰다. */
    static long elapsedMillis(StepExecution stepExecution) {
        LocalDateTime start = stepExecution.getStartTime();
        LocalDateTime end = stepExecution.getEndTime() == null ? LocalDateTime.now() : stepExecution.getEndTime();
        if (start == null) {
            return 0L;
        }
        return Duration.between(start, end).toMillis();
    }

    /** 첫 실패 예외를 "클래스명: 메시지 첫 줄"로 요약한다. 스택·응답 본문은 남기지 않는다. */
    static String firstFailure(StepExecution stepExecution) {
        return stepExecution.getFailureExceptions().stream()
                .findFirst()
                .map(e -> e.getClass().getSimpleName() + ": " + firstLine(e.getMessage()))
                .orElse("(원인 미기록)");
    }

    /** 메시지의 첫 줄만 반환한다. */
    static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
