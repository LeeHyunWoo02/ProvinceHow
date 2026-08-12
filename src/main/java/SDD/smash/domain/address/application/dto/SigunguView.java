package SDD.smash.domain.address.application.dto;

import SDD.smash.domain.address.domain.model.Sigungu;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;

/** 시군구 조회 결과. */
public record SigunguView(SigunguCode code, String name, SidoCode sidoCode) {

    public static SigunguView from(Sigungu sigungu) {
        return new SigunguView(sigungu.code(), sigungu.name(), sigungu.sidoCode());
    }
}
