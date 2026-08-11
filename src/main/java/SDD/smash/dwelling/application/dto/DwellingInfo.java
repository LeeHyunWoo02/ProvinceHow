package SDD.smash.dwelling.application.dto;

import SDD.smash.common.domain.model.Money;
import SDD.smash.dwelling.domain.model.DwellingMarket;
import SDD.smash.dwelling.domain.model.DwellingType;

/**
 * 시군구의 월세·전세 평균과 중앙값. As-Is {@code DwellingInfoDTO} 자리를 대신한다.
 *
 * <p>실거래가 없으면 각 필드가 비어(null) 있을 수 있다. 이는 오류가 아니라 정상 상태다.
 */
public record DwellingInfo(Double monthAvg, Money monthMid,
                           Double jeonseAvg, Money jeonseMid) {

    public static DwellingInfo from(DwellingMarket market) {
        return new DwellingInfo(
                market.averageOf(DwellingType.MONTHLY).orElse(null),
                market.medianOf(DwellingType.MONTHLY).orElse(null),
                market.averageOf(DwellingType.JEONSE).orElse(null),
                market.medianOf(DwellingType.JEONSE).orElse(null));
    }
}
