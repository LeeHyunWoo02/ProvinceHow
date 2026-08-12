package SDD.smash.domain.dwelling.domain.model;

import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.util.Optional;

/**
 * 지역의 전월세 시세 (Aggregate Root).
 *
 * <p>시군구는 다른 Aggregate 이므로 객체가 아니라 {@link SigunguCode} 로만 참조한다.
 */
public class DwellingMarket {

    private final SigunguCode sigunguCode;
    private final RentStat monthly;
    private final RentStat jeonse;

    private DwellingMarket(SigunguCode sigunguCode, RentStat monthly, RentStat jeonse) {
        if (sigunguCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
        this.sigunguCode = sigunguCode;
        this.monthly = monthly == null ? RentStat.EMPTY : monthly;
        this.jeonse = jeonse == null ? RentStat.EMPTY : jeonse;
    }

    /** 저장소에서 복원할 때 쓴다. */
    public static DwellingMarket reconstitute(SigunguCode sigunguCode, RentStat monthly, RentStat jeonse) {
        return new DwellingMarket(sigunguCode, monthly, jeonse);
    }

    /** 해당 유형의 시세 중앙값. 실거래가 없으면 비어 있다. */
    public Optional<Money> medianOf(DwellingType type) {
        return Optional.ofNullable(statOf(type).median());
    }

    /** 해당 유형의 시세 평균. 실거래가 없으면 비어 있다. */
    public Optional<Double> averageOf(DwellingType type) {
        return Optional.ofNullable(statOf(type).average());
    }

    private RentStat statOf(DwellingType type) {
        return type == DwellingType.MONTHLY ? monthly : jeonse;
    }

    public SigunguCode sigunguCode() {
        return sigunguCode;
    }

    public RentStat monthly() {
        return monthly;
    }

    public RentStat jeonse() {
        return jeonse;
    }
}
