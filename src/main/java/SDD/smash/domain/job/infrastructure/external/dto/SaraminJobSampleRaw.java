package SDD.smash.domain.job.infrastructure.external.dto;

/**
 * 사람인 응답의 {@code jobs.job[]} 한 건에서 <b>프로필 집계에 필요한 원자료</b>만 뽑은 기술 DTO.
 * 카드 표시용 {@link SaraminJobVacancyRaw} 와 달리 연봉·경력·업종만 담는다.
 *
 * <p>이 타입은 {@code infrastructure/external} 밖으로 나가지 않는다. 어댑터가 연봉 문자열 파싱과
 * 경력 코드 해석을 거쳐 {@code JobPostingSample}(도메인)으로 옮긴 뒤 버린다. 값은 사람인 원문이다.
 *
 * <p>연봉은 {@code salary.name} 파싱만 쓴다({@code salary.code}는 집계에 쓰지 않아 담지 않는다).
 *
 * @param salaryName     {@code salary.name}(예: "3,000~4,000만원")
 * @param experienceCode {@code position.experience-level.code}(1신입/2경력/3신입·경력/0경력무관)
 * @param industryName   {@code position.industry.name}
 */
public record SaraminJobSampleRaw(String salaryName,
                                  String experienceCode,
                                  String industryName) {
}
