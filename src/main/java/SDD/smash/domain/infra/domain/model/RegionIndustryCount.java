package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 시군구 × 업종의 영업중 사업장 개수. 수집 결과를 통계 계산에 넘기는 입력 단위다.
 *
 * <p>수집 원천(공식 API / 벌크 CSV / 레거시 CSV)이 무엇이든 여기까지 오면 형태가 같다.
 * 그래서 통계 계산({@code InfraStatPolicy})은 원천을 알지 못한다.
 */
public record RegionIndustryCount(SigunguCode sigunguCode, IndustryCode industryCode, int count) {

    public RegionIndustryCount {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
        if (industryCode == null) {
            throw new DomainException(ErrorCode.INDUSTRY_CODE_NOT_FOUND, "업종 코드는 필수입니다.");
        }
        if (count < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "인프라 개수는 0 이상이어야 합니다.");
        }
    }

    /** 같은 시군구·업종을 여러 인허가기관에서 모았을 때 합친다. */
    public RegionIndustryCount plus(int more) {
        return new RegionIndustryCount(sigunguCode, industryCode, count + more);
    }
}
