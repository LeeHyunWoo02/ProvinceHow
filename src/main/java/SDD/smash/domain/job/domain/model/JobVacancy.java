package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.time.LocalDate;

/**
 * 개별 채용공고 한 건. {@code JobCount}(집계)와 달리 화면 카드에 그대로 보여줄
 * 풍부한 표시 필드를 담는 값 객체다.
 *
 * <p><b>표시 목적이다.</b> 코드값이 아니라 사람이 읽는 이름을 담는다
 * (지역명·직종명·연봉 문구 등은 외부 공급자가 준 라벨 그대로다). 외부 공급자의 어휘는
 * 어댑터가 이 타입으로 옮기는 순간 사라지고, 여기 남은 것은 전부 표시용 문자열이다.
 *
 * <p>불변식: 카드로서 의미를 가지려면 <b>식별자와 제목</b>이 있어야 한다. 나머지 필드는
 * 공급자가 비워 줄 수 있으므로 {@code null}/빈 값을 허용한다. 불변식 위반은 어댑터가
 * {@link DomainException} 을 잡아 그 공고만 건너뛴다(전체 목록은 계속 만든다).
 *
 * @param id             공고 식별자
 * @param title          공고 제목
 * @param companyName    기업명
 * @param detailUrl      공고 상세 페이지 URL
 * @param regionName     근무지 지역명(공급자 라벨 그대로)
 * @param jobName        직종명(공급자 라벨 그대로)
 * @param salaryText     연봉/급여 문구
 * @param experienceText 경력 조건 문구
 * @param educationText  학력 조건 문구
 * @param employmentType 고용형태 문구
 * @param active         공고가 아직 열려 있는가
 * @param postingDate    게시일(모르면 {@code null})
 * @param expirationDate 마감일(모르면 {@code null})
 */
public record JobVacancy(JobPostingId id,
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

    public JobVacancy {
        if (id == null) {
            throw new DomainException(ErrorCode.JOB_VACANCY_INVALID, "채용공고 식별자는 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new DomainException(ErrorCode.JOB_VACANCY_INVALID, "채용공고 제목은 필수입니다.");
        }
    }
}
