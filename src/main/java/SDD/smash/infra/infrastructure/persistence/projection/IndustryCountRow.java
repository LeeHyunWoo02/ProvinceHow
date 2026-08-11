package SDD.smash.infra.infrastructure.persistence.projection;

import SDD.smash.infra.domain.model.Major;

import java.math.BigDecimal;

/** 업종별 인프라 상세 프로젝션의 기술 DTO. */
public record IndustryCountRow(Major major, String industryName, Integer count, BigDecimal ratio) {
}
