package SDD.smash.domain.dwelling.infrastructure.batch.dto;

import SDD.smash.global.domain.model.SigunguCode;

import java.time.YearMonth;

/** 배치 한 건의 작업 단위 — 시군구 하나에 대한 조회 기간. */
public record WorkItem(SigunguCode sigunguCode, YearMonth from, YearMonth to) {
}
