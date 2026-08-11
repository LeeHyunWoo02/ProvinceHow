package SDD.smash.job.domain.model;

import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

/**
 * 직종 코드. 대분류(2자리)와 중분류(3자리)에 모두 쓰는 컨텍스트 로컬 값 객체다.
 *
 * <p>As-Is 의 {@code String topCode} / {@code midJobCode} 를 대체한다.
 * 자릿수는 검증하지 않는다 — As-Is 도 형식이 아니라 <b>존재 여부</b>로만 판정했고
 * (배치가 {@code addLeadingZero}/{@code addLeadingZeroThird} 로 이미 정규화한다),
 * 여기서 자릿수를 강제하면 지금 통과하던 입력이 막혀 동작이 바뀐다.
 */
public record JobCode(String value) {

    public JobCode {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다.");
        }
    }

    public static JobCode of(String value) {
        return new JobCode(value);
    }
}
