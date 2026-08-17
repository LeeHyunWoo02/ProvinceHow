package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobVacancyTest {

    @Test
    @DisplayName("식별자와 제목이 있으면 카드를 만든다")
    void createsVacancyWithIdAndTitle() {
        // when
        JobVacancy vacancy = new JobVacancy(
                JobPostingId.of("46203390"), "백엔드 개발자", "스매시", "https://saramin/1",
                "서울 > 강남구", "웹개발", "회사내규", "신입", "대졸", "정규직",
                true, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // then
        assertThat(vacancy.title()).isEqualTo("백엔드 개발자");
        assertThat(vacancy.active()).isTrue();
        assertThat(vacancy.expirationDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("식별자가 없으면 DomainException(JOB_VACANCY_INVALID)")
    void rejectsNullId() {
        assertThatThrownBy(() -> new JobVacancy(
                null, "제목", null, null, null, null, null, null, null, null, true, null, null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.JOB_VACANCY_INVALID);
    }

    @Test
    @DisplayName("제목이 비어 있으면 DomainException(JOB_VACANCY_INVALID)")
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new JobVacancy(
                JobPostingId.of("1"), "  ", null, null, null, null, null, null, null, null, true, null, null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.JOB_VACANCY_INVALID);
    }
}
