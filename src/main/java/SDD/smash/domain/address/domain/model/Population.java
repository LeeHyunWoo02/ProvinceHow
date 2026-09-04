package SDD.smash.domain.address.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 시군구의 인구수. {@link Sigungu} Aggregate 안에 속한다.
 *
 * <p>독립적으로 조회·변경되지 않으며 항상 자신이 속한 시군구 코드를 함께 갖는다.
 */
public record Population(SigunguCode sigunguCode, int count) {

    public Population {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
        if (count < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "인구수는 0 이상이어야 합니다.");
        }
    }

    public static Population of(SigunguCode sigunguCode, int count) {
        return new Population(sigunguCode, count);
    }
}
