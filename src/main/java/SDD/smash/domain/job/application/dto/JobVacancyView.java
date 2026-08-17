package SDD.smash.domain.job.application.dto;

import SDD.smash.domain.job.domain.model.JobVacancy;

import java.time.LocalDate;

/**
 * 개별 채용공고 조회 결과(유스케이스 출력). {@code job} 컨텍스트가 다른 컨텍스트에 넘기는 표시용 DTO다.
 *
 * <p>도메인 모델({@link JobVacancy})을 그대로 노출하지 않는다. 식별자는 문자열로 편다.
 */
public record JobVacancyView(String postingId,
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

    public static JobVacancyView from(JobVacancy v) {
        return new JobVacancyView(
                v.id().value(),
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
