package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.DwellingTypeItem;

/**
 * 지역 상세 응답에 실리는 주택유형별 시세 한 건. HTTP 응답 계약(JSON 필드명)의 소유자다.
 * 금액 단위는 만원이고, 실거래가 없는 유형은 통계 필드가 null 이다.
 */
public record DwellingTypeEntry(String housingType,
                                Double monthAvg, Integer monthMid,
                                Double jeonseAvg, Integer jeonseMid) {

    public static DwellingTypeEntry from(DwellingTypeItem item) {
        return new DwellingTypeEntry(
                item.housingType(),
                item.monthAvg(), item.monthMid(),
                item.jeonseAvg(), item.jeonseMid());
    }
}
