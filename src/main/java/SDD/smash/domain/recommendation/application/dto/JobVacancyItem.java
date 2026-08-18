package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.JobVacancyView;

import java.time.LocalDate;

/**
 * 추천 지역 상세에 실리는 개별 채용공고 카드(recommendation 로컬 요약 DTO).
 *
 * <p>다른 컨텍스트 결과를 recommendation 소유 타입으로 재포장하는 이 컨텍스트의 관례를 따른다
 * ({@code JobInfoSummary.from}, {@code DwellingInfoSummary.from} 과 같은 방식). 이렇게 해서
 * {@code RegionDetailInfo} 가 job 컨텍스트의 {@code JobVacancyView} 를 직접 품지 않게 한다.
 */
public record JobVacancyItem(String postingId,
                             String title,
                             String companyName,
                             String detailUrl,
                             String regionName,
                             String jobName,
                             String salaryText,
                             String experienceText,
                             String educationText,
                             String employmentType,
                             boolean active,
                             LocalDate postingDate,
                             LocalDate expirationDate) {

    public static JobVacancyItem from(JobVacancyView v) {
        return new JobVacancyItem(
                v.postingId(),
                v.title(),
                v.companyName(),
                v.detailUrl(),
                v.regionName(),
                v.jobName(),
                v.salaryText(),
                v.experienceText(),
                v.educationText(),
                v.employmentType(),
                v.active(),
                v.postingDate(),
                v.expirationDate());
    }
}
