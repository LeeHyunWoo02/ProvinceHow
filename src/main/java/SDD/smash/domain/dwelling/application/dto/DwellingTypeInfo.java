package SDD.smash.domain.dwelling.application.dto;

import SDD.smash.domain.dwelling.domain.model.DwellingTypeStat;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.global.domain.model.Money;

/**
 * 주택유형 하나의 월세·전세 평균과 중앙값. {@link DwellingInfo}(3종 통합)의 유형별 판이다.
 *
 * <p>{@code DwellingInfo} 와 같이 {@code Money} 를 그대로 담고, 유형 식별자도 dwelling 의
 * 도메인 enum 을 그대로 노출한다(자기 컨텍스트 application DTO 는 자기 도메인 타입을 담아도 된다).
 * 원시 타입으로 푸는 것은 호출 측 경계의 몫이다.
 *
 * <p>실거래가 없는 유형은 각 통계 필드가 비어(null) 있을 수 있다 — 정상 상태다.
 */
public record DwellingTypeInfo(HousingType housingType,
                               Double monthAvg, Money monthMid,
                               Double jeonseAvg, Money jeonseMid) {

    public static DwellingTypeInfo from(DwellingTypeStat stat) {
        return new DwellingTypeInfo(
                stat.housingType(),
                stat.monthly().average(), stat.monthly().median(),
                stat.jeonse().average(), stat.jeonse().median());
    }
}
