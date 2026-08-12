package SDD.smash.domain.infra.domain.model;

/**
 * 한 시군구·한 인프라 대분류의 개수 총합과 평균 점수. 조회 전용 모델(CQRS-lite)이다.
 *
 * <p>As-Is {@code InfraMajor} 에 해당한다. 이 값은 화면에 그대로 보여주는 통계이지
 * 추천 점수(0~100, {@code common.Score})가 아니므로 범위를 강제하지 않는다.
 */
public record MajorInfraSummary(Major major, long count, Double averageScore) {
}
