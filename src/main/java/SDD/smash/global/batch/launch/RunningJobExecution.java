package SDD.smash.global.batch.launch;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * {@code END_TIME IS NULL} 인 배치 실행 한 건. 나이 판정에 쓸 두 시각을 함께 들고 온다.
 *
 * @param startedAt   실행 시작 시각. "몇 시간째 STARTED 인가" 를 나타낸다
 * @param heartbeatAt 이 실행에 속한 StepExecution 의 최신 {@code LAST_UPDATED}(스텝이 없으면 {@code CREATE_TIME}).
 *                    Job 행의 {@code LAST_UPDATED} 는 시작/종료 때만 갱신돼 하트비트가 아니다 —
 *                    청크 커밋마다 저장되는 StepExecution 쪽이 진짜 진행 신호다
 */
public record RunningJobExecution(long jobExecutionId, String jobName,
                                  LocalDateTime startedAt, LocalDateTime heartbeatAt) {

    /** 마지막 하트비트 이후 흐른 시간. 고아 판정 기준이다. */
    public Duration idleFor(LocalDateTime now) {
        return Duration.between(heartbeatAt, now);
    }

    /** 시작 이후 흐른 시간. 관측 지표(가장 오래된 실행 나이)의 기준이다. */
    public Duration runningFor(LocalDateTime now) {
        return Duration.between(startedAt, now);
    }
}
