package SDD.smash.infra.domain.model;

import java.math.BigDecimal;

/**
 * 한 시군구·한 업종의 인프라 개수·비율. {@link RegionInfra} Aggregate 안에 속한다.
 *
 * <p>As-Is {@code InfraDetails} 프로젝션과 같은 모양이다. 그 프로젝션이 업종 코드나
 * 점수를 담지 않았으므로(대분류·이름·개수·비율만) 여기도 그 네 필드만 갖는다.
 */
public record IndustryCount(Major major, String industryName, int count, BigDecimal ratio) {
}
