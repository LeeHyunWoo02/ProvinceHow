package SDD.smash.domain.infra.infrastructure.persistence.projection;

import SDD.smash.domain.infra.domain.model.Major;

/** 전 시군구 대분류별 평균점수 프로젝션의 기술 DTO. */
public record RegionMajorScoreRow(String sigunguCode, Major major, Double averageScore) {
}
