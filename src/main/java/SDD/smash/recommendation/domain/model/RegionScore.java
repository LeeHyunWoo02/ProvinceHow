package SDD.smash.recommendation.domain.model;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SidoCode;
import SDD.smash.common.domain.model.SigunguCode;

/**
 * 한 시군구의 조합 점수. {@code recommendation} 은 별도 Aggregate를 갖지 않는
 * 조합 전용 컨텍스트라(architecture-conventions §2) 이 레코드는 여러 컨텍스트의 점수를
 * 합친 결과를 나르는 값 객체일 뿐이다.
 */
public record RegionScore(SigunguCode sigunguCode, SidoCode sidoCode, Score score) {
}
