package SDD.smash.infra.domain.model;

import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

import java.util.List;

/**
 * 한 시군구의 인프라 현황 (Aggregate Root). 업종별 개수 목록을 포함한다.
 *
 * <p>As-Is 는 {@code Infra} 팩트 테이블의 행 하나하나가 "시군구 × 업종"이었다.
 * 여기서는 시군구 기준으로 모아 하나의 Aggregate 로 다룬다.
 * 내부 항목은 Aggregate Root 를 통해서만 꺼낸다({@link #industryCounts()}).
 */
public class RegionInfra {

    private final List<IndustryCount> industryCounts;

    private RegionInfra(List<IndustryCount> industryCounts) {
        if (industryCounts == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "인프라 목록은 필수입니다.");
        }
        this.industryCounts = List.copyOf(industryCounts);
    }

    public static RegionInfra reconstitute(List<IndustryCount> industryCounts) {
        return new RegionInfra(industryCounts);
    }

    public List<IndustryCount> industryCounts() {
        return industryCounts;
    }
}
