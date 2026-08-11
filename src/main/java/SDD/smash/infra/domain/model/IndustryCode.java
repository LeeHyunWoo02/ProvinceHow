package SDD.smash.infra.domain.model;

import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

/**
 * 업종 코드. {@code infra} 컨텍스트 로컬 값 객체다.
 *
 * <p>As-Is 는 형식이 아니라 <b>존재 여부</b>로만 판정했다(배치가 이미 정제한 코드만 들어온다).
 * 자릿수를 강제하면 지금 통과하던 입력이 막혀 동작이 바뀌므로 공백만 검증한다.
 */
public record IndustryCode(String value) {

    public IndustryCode {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.INDUSTRY_CODE_NOT_FOUND, "유효하지 않은 업종 코드입니다.");
        }
    }

    public static IndustryCode of(String value) {
        return new IndustryCode(value);
    }
}
