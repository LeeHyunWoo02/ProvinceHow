package SDD.smash.recommendation.application.dto;

import SDD.smash.dwelling.application.dto.DwellingSimpleInfo;

/** 추천 목록용 축약 시세. As-Is {@code DwellingSimpleInfoDTO} 자리를 대신한다. */
public record DwellingSimpleInfoSummary(Integer monthMid, Integer jeonseMid) {

    public static DwellingSimpleInfoSummary from(DwellingSimpleInfo info) {
        if (info == null) {
            return null;
        }
        return new DwellingSimpleInfoSummary(
                info.monthMid() == null ? null : info.monthMid().manwon(),
                info.jeonseMid() == null ? null : info.jeonseMid().manwon());
    }
}
