package SDD.smash.domain.job.domain.model;

/**
 * 일자리 점수 캐시의 도메인 식별자.
 *
 * <p>직종 코드가 없으면(전체 일자리 기준) {@code jobCode} 가 {@code null} 이다.
 * As-Is 가 키에 {@code "default"} 리터럴을 쓰던 자리이며, 그 문자열 치환은 어댑터가 한다.
 */
public record JobScoreKey(JobCode jobCode) {

    public static JobScoreKey of(JobCode jobCode) {
        return new JobScoreKey(jobCode);
    }

    /** 직종을 가리지 않는 전체 일자리 기준 키 */
    public static JobScoreKey all() {
        return new JobScoreKey(null);
    }

    public boolean isAllJobs() {
        return jobCode == null;
    }
}
