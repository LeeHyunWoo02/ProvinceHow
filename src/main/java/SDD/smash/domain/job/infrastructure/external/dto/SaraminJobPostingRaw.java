package SDD.smash.domain.job.infrastructure.external.dto;

/**
 * 사람인 응답의 {@code jobs.job[]} 한 건을 <b>번역 전 원문 그대로</b> 담은 기술 DTO.
 *
 * <p>워크넷과 달리 사람인은 공고당 지역/직종이 <b>단일 값</b>이라 {@code List} 가 아니라 단일 문자열이다.
 *
 * <p>이 타입은 {@code infrastructure/external} 밖으로 나가지 않는다.
 * 어댑터가 {@code JobPosting}(도메인)으로 옮긴 뒤 버린다.
 *
 * @param postingId  공고 식별자({@code id})
 * @param regionCode 사람인 지역코드({@code loc_cd}). 사람인 코드 체계 그대로다
 * @param jobCode    사람인 직종코드({@code job_cd}). 사람인 코드 체계 그대로다
 */
public record SaraminJobPostingRaw(String postingId, String regionCode, String jobCode) {
}
