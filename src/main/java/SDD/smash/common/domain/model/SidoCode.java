package SDD.smash.common.domain.model;

import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

/**
 * 시도 코드. 법정동 코드의 앞 2자리다.
 *
 * <p>공유 커널. 모든 컨텍스트가 같은 의미로 쓴다.
 */
public record SidoCode(String value) {

    private static final int LENGTH = 2;

    public SidoCode {
        if (!isValid(value)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시도 코드");
        }
    }

    public static SidoCode of(String value) {
        return new SidoCode(value);
    }

    private static boolean isValid(String value) {
        return value != null
                && value.length() == LENGTH
                && value.chars().allMatch(Character::isDigit);
    }
}
