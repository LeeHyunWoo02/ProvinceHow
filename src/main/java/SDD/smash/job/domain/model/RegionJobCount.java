package SDD.smash.job.domain.model;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

/**
 * 한 시군구의 일자리 수. 점수 계산의 입력이다.
 *
 * <p>전체 합계일 수도 있고 특정 중분류의 개수일 수도 있다. 어느 쪽이든 계산 규칙은 같으므로
 * 하나의 타입으로 다룬다. As-Is {@code JobCountDTO} 가 두 생성자로 같은 일을 하던 것과 같다.
 *
 * <p>합계가 {@code null} 인 행은 As-Is 가 0 으로 바꿔 담았다. 그 보정은 어댑터가 한다.
 */
public record RegionJobCount(SigunguCode sigunguCode, long count) {

    public RegionJobCount {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
    }
}
