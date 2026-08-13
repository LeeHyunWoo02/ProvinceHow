package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * {@code infra} 테이블 한 행에 해당하는 통계. {@code InfraStatPolicy} 의 산출물이다.
 *
 * <p>{@link InfraScore} 가 생성자에서 {@code [0, 100]} 을 강제하므로,
 * 이 타입으로 만들어진 값은 추천 경로에서 {@code SCORE_OUT_OF_RANGE} 를 일으키지 않는다.
 */
public record RegionIndustryStat(SigunguCode sigunguCode, IndustryCode industryCode,
                                 int count, InfraRatio ratio, InfraScore score) {

    public RegionIndustryStat {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
        if (industryCode == null) {
            throw new DomainException(ErrorCode.INDUSTRY_CODE_NOT_FOUND, "업종 코드는 필수입니다.");
        }
        if (count < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "인프라 개수는 0 이상이어야 합니다.");
        }
        if (ratio == null || score == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "비중과 점수는 필수입니다.");
        }
    }
}
