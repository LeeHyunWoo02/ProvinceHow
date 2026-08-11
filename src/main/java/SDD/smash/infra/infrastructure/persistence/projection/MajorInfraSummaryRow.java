package SDD.smash.infra.infrastructure.persistence.projection;

import SDD.smash.infra.domain.model.Major;

/** 시군구·대분류별 개수 총합·평균점수 프로젝션의 기술 DTO. {@code SUM(int)} 은 {@code Long} 이다. */
public record MajorInfraSummaryRow(Major major, Long count, Double averageScore) {
}
