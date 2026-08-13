package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 채용공고의 식별자. 외부 채용정보 공급자가 공고마다 붙이는 고유번호다.
 *
 * <p>같은 공고가 페이지 경계에서 두 번 내려오는 일이 있어(수집 도중 목록이 밀린다)
 * 중복 제거의 기준이 된다. 형식은 공급자마다 다르므로 자릿수를 강제하지 않고
 * <b>비어 있지 않을 것</b>만 요구한다.
 */
public record JobPostingId(String value) {

    public JobPostingId {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 채용공고 식별자입니다.");
        }
        value = value.trim();
    }

    public static JobPostingId of(String value) {
        return new JobPostingId(value);
    }
}
