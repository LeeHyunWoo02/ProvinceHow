package SDD.smash.domain.job.infrastructure.external.dto;

/**
 * 사람인 응답의 {@code jobs.job[]} 한 건을 <b>표시용 필드까지</b> 뽑아 담은 기술 DTO.
 * 집계용 {@link SaraminJobPostingRaw}(id·코드만) 와 달리 카드에 필요한 라벨을 모두 담는다.
 *
 * <p>이 타입은 {@code infrastructure/external} 밖으로 나가지 않는다. 어댑터가
 * {@code JobVacancy}(도메인)로 옮긴 뒤 버린다. 필드는 전부 사람인 원문 문자열이다.
 *
 * @param id                   {@code id}
 * @param title                {@code position.title}
 * @param companyName          {@code company.detail.name}
 * @param detailUrl            {@code url}
 * @param regionName           {@code position.location.name}
 * @param jobName              {@code position.job-code.name}
 * @param salaryText           {@code salary.name}
 * @param experienceText       {@code position.experience-level.name}
 * @param educationText        {@code position.required-education-level.name}
 * @param employmentType       {@code position.job-type.name}
 * @param active               {@code active}(1/0)
 * @param postingTimestamp     {@code posting-timestamp}(epoch seconds 문자열)
 * @param expirationTimestamp  {@code expiration-timestamp}(epoch seconds 문자열)
 */
public record SaraminJobVacancyRaw(String id,
                                   String title,
                                   String companyName,
                                   String detailUrl,
                                   String regionName,
                                   String jobName,
                                   String salaryText,
                                   String experienceText,
                                   String educationText,
                                   String employmentType,
                                   String active,
                                   String postingTimestamp,
                                   String expirationTimestamp) {
}
