package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.dwelling.application.dto.DwellingTypeInfo;

/**
 * 상세 조회용 주택유형별 시세 한 건. {@link DwellingInfoSummary}(3종 통합)와 같은 방식으로
 * {@code Money} 를 만원 단위 {@code Integer} 로 풀고, 유형 식별자는 enum 이름 문자열로 담는다.
 */
public record DwellingTypeItem(String housingType,
                               Double monthAvg, Integer monthMid,
                               Double jeonseAvg, Integer jeonseMid) {

    public static DwellingTypeItem from(DwellingTypeInfo info) {
        if (info == null) {
            return null;
        }
        return new DwellingTypeItem(
                info.housingType() == null ? null : info.housingType().name(),
                info.monthAvg(),
                info.monthMid() == null ? null : info.monthMid().manwon(),
                info.jeonseAvg(),
                info.jeonseMid() == null ? null : info.jeonseMid().manwon());
    }
}
