package SDD.smash.domain.address.domain.model;

import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;

/**
 * 시도-시군구를 한 줄로 합친 조회 전용 모델.
 *
 * <p>두 Aggregate 를 합쳐 보여주기만 하므로 Aggregate 로 만들지 않는다.
 * 비즈니스 규칙을 담지 않는다.
 */
public record RegionCode(SidoCode sidoCode, String sidoName,
                         SigunguCode sigunguCode, String sigunguName) {
}
