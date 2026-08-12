package SDD.smash.global.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 금액. 단위는 <b>만원</b>이다.
 *
 * <p>전월세 시세(월세 중앙값·전세 중앙값)와 사용자 예산이 모두 만원 단위라서,
 * 단위를 타입에 못박아 원 단위 값이 섞여드는 사고를 막는다.
 */
public record Money(int manwon) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (manwon < 0) {
            throw new DomainException(ErrorCode.PRICE_AMOUNT_NOT_VALID, "금액은 0 이상이어야 합니다.");
        }
    }

    public static Money of(int manwon) {
        return new Money(manwon);
    }

    /** 두 금액의 차이. 부호는 없다. */
    public int diffTo(Money other) {
        return Math.abs(manwon - other.manwon);
    }

    public boolean isAtLeast(Money other) {
        return manwon >= other.manwon;
    }

    public boolean isZero() {
        return manwon == 0;
    }
}
