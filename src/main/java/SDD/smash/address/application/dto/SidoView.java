package SDD.smash.address.application.dto;

import SDD.smash.address.domain.model.Sido;
import SDD.smash.common.domain.model.SidoCode;

/** 시도 조회 결과. 다른 컨텍스트는 address 의 도메인 모델 대신 이 뷰를 본다. */
public record SidoView(SidoCode code, String name) {

    public static SidoView from(Sido sido) {
        return new SidoView(sido.code(), sido.name());
    }
}
