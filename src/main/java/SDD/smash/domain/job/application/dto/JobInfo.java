package SDD.smash.domain.job.application.dto;

/**
 * 지역의 일자리 수와 그 목록을 볼 수 있는 외부 링크.
 * As-Is {@code JobInfoDTO} 자리를 대신한다.
 */
public record JobInfo(long count, String url) {
}
