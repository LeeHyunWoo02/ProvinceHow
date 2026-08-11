package SDD.smash.infra.domain.model;

import SDD.smash.common.domain.model.SigunguCode;

/**
 * 한 시군구·한 인프라 대분류의 평균 점수. 전 시군구 점수 계산의 원천 데이터다.
 * As-Is {@code SigunguMajorAvgDTO} 에 해당한다.
 */
public record RegionMajorScore(SigunguCode sigunguCode, Major major, Double averageScore) {
}
