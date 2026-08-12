package SDD.smash.global.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 시군구 코드. 법정동 코드의 앞 5자리다.
 *
 * <p>공유 커널이자 컨텍스트 사이를 잇는 유일한 식별자다.
 * 각 컨텍스트는 {@code Sigungu} Aggregate 를 공유하지 않고 이 값 객체만 주고받는다.
 */
public record SigunguCode(String value) {

    private static final int LENGTH = 5;
    private static final int SIDO_PREFIX_LENGTH = 2;

    public SigunguCode {
        if (!isValid(value)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드");
        }
    }

    public static SigunguCode of(String value) {
        return new SigunguCode(value);
    }

    /** 시군구 코드의 앞 2자리가 곧 시도 코드다. */
    public SidoCode sidoCode() {
        return new SidoCode(value.substring(0, SIDO_PREFIX_LENGTH));
    }

    private static boolean isValid(String value) {
        return value != null
                && value.length() == LENGTH
                && value.chars().allMatch(Character::isDigit);
    }
}
