package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.JobVacancyItem;

import java.time.LocalDate;

/**
 * 지역 상세 응답에 실리는 개별 채용공고 카드. HTTP 응답 계약(JSON 필드명)의 소유자다.
 * recommendation 로컬 요약({@link JobVacancyItem})을 표현 계층 타입으로 옮긴 것이다.
 */
public record JobVacancyEntry(String postingId,
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

    public static JobVacancyEntry from(JobVacancyItem v) {
        return new JobVacancyEntry(
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
