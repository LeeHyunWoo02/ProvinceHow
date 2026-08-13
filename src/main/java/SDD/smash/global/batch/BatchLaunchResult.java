package SDD.smash.global.batch;

import org.springframework.batch.core.JobExecution;

import java.util.Optional;

/**
 * 배치 기동 시도의 결과. 예외로 흐름을 만들지 않기 위해 값으로 돌려준다.
 *
 * @param status    기동 결과
 * @param execution 실제로 기동했을 때만 존재한다
 * @param reason    건너뛰거나 실패한 사유
 */
public record BatchLaunchResult(Status status, JobExecution execution, String reason) {

    public enum Status {
        /** 기동했다. {@code execution} 에 결과가 있다 */
        LAUNCHED,
        /** 이미 실행 중이라 건너뛰었다 (중복 실행 방지) */
        SKIPPED_RUNNING,
        /** 같은 JobParameters 로 이미 완료됐다 (멱등) */
        SKIPPED_ALREADY_COMPLETE,
        /** 설정/프로퍼티 문제로 기동하지 못했다 */
        REJECTED,
        /** 기동 자체가 예외로 끝났다 */
        FAILED
    }

    public static BatchLaunchResult launched(JobExecution execution) {
        return new BatchLaunchResult(Status.LAUNCHED, execution, null);
    }

    public static BatchLaunchResult of(Status status, String reason) {
        return new BatchLaunchResult(status, null, reason);
    }

    public boolean isLaunched() {
        return status == Status.LAUNCHED;
    }

    public Optional<JobExecution> jobExecution() {
        return Optional.ofNullable(execution);
    }
}
