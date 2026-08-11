package SDD.smash.dwelling.application.dto;

import SDD.smash.common.domain.model.Money;
import SDD.smash.dwelling.domain.model.DwellingMarket;
import SDD.smash.dwelling.domain.model.DwellingType;

/**
 * 추천 목록에 쓰는 축약 시세. 중앙값만 담는다.
 * As-Is {@code DwellingSimpleInfoDTO} 자리를 대신한다.
 */
public record DwellingSimpleInfo(Money monthMid, Money jeonseMid) {

    public static DwellingSimpleInfo from(DwellingMarket market) {
        return new DwellingSimpleInfo(
                market.medianOf(DwellingType.MONTHLY).orElse(null),
                market.medianOf(DwellingType.JEONSE).orElse(null));
    }
}
