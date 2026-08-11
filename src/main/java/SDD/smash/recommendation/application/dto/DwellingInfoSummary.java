package SDD.smash.recommendation.application.dto;

import SDD.smash.dwelling.application.dto.DwellingInfo;

/** 상세 조회용 시세(평균+중앙값). As-Is {@code DwellingInfoDTO} 자리를 대신한다. */
public record DwellingInfoSummary(Double monthAvg, Integer monthMid, Double jeonseAvg, Integer jeonseMid) {

    public static DwellingInfoSummary from(DwellingInfo info) {
        if (info == null) {
            return null;
        }
        return new DwellingInfoSummary(
                info.monthAvg(),
                info.monthMid() == null ? null : info.monthMid().manwon(),
                info.jeonseAvg(),
                info.jeonseMid() == null ? null : info.jeonseMid().manwon());
    }
}
