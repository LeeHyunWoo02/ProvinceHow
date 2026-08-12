package SDD.smash.domain.address.application.dto;

import SDD.smash.domain.address.domain.model.RegionCode;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;

/** 시도-시군구를 합친 조회 결과. As-Is 의 {@code CodeNameDTO} 자리를 대신한다. */
public record RegionCodeView(SidoCode sidoCode, String sidoName,
                             SigunguCode sigunguCode, String sigunguName) {

    public static RegionCodeView from(RegionCode regionCode) {
        return new RegionCodeView(
                regionCode.sidoCode(),
                regionCode.sidoName(),
                regionCode.sigunguCode(),
                regionCode.sigunguName());
    }
}
