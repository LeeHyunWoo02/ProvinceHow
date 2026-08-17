package SDD.smash.domain.job.domain.model;

import java.util.OptionalInt;

/**
 * 지역 채용 프로필 계산에 쓰는 <b>표본 데이터 포인트</b>. 개별 공고에서 프로필 지표 계산에
 * 필요한 원자료만 뽑아 담은 값 객체다(카드 표시용 {@link JobVacancy} 와 다르다).
 *
 * <p>외부 공급자의 어휘는 어댑터가 이 타입으로 옮기는 순간 사라진다. 연봉 문자열 파싱·경력 코드
 * 해석은 어댑터(infrastructure)가 하고, 여기 남은 값은 이미 정제된 숫자/열거형이다.
 * 파싱 불가한 값은 {@code null}/{@link ExperienceLevel#UNKNOWN} 으로 누락을 허용한다 —
 * 집계 규칙이 "확인된 표본만" 세도록 만들기 위함이다.
 *
 * @param salaryMinManwon 연봉 하한(만원). 파싱 불가면 {@code null}
 * @param salaryMaxManwon 연봉 상한(만원). 파싱 불가면 {@code null}
 * @param experienceLevel 경력 구분. 미상이면 {@link ExperienceLevel#UNKNOWN}
 * @param industryName    업종명. 없으면 {@code null}/빈 값
 */
public record JobPostingSample(Integer salaryMinManwon,
                               Integer salaryMaxManwon,
                               ExperienceLevel experienceLevel,
                               String industryName) {

    public JobPostingSample {
        experienceLevel = (experienceLevel == null) ? ExperienceLevel.UNKNOWN : experienceLevel;
    }

    /**
     * 이 표본의 연봉 대표값(만원). 상·하한이 모두 있으면 중간값, 하나만 있으면 그 값,
     * 둘 다 없으면 비어 있음. 지역 연봉 중앙값은 표본들의 이 대표값으로 계산한다.
     */
    public OptionalInt salaryMidpointManwon() {
        if (salaryMinManwon != null && salaryMaxManwon != null) {
            return OptionalInt.of((salaryMinManwon + salaryMaxManwon) / 2);
        }
        if (salaryMinManwon != null) {
            return OptionalInt.of(salaryMinManwon);
        }
        if (salaryMaxManwon != null) {
            return OptionalInt.of(salaryMaxManwon);
        }
        return OptionalInt.empty();
    }

    public boolean hasIndustry() {
        return industryName != null && !industryName.isBlank();
    }
}
