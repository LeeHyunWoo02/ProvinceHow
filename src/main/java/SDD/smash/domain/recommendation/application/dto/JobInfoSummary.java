package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.JobInfo;

/**
 * 일자리 수와 목록 링크. As-Is {@code JobInfoDTO} 자리를 대신한다.
 *
 * <p>{@code recommend}/{@code detail} 양쪽 응답에 그대로 임베드되므로 여기 둔다
 * ({@code OpenAI} 패키지가 이 값을 상위 DTO를 통해 그대로 전달하기만 하므로,
 * application/presentation 경계를 넘어 재사용해도 무해하다 — 보고에 명시).
 */
public record JobInfoSummary(long count, String url) {

    public static JobInfoSummary from(JobInfo info) {
        return info == null ? null : new JobInfoSummary(info.count(), info.url());
    }
}
