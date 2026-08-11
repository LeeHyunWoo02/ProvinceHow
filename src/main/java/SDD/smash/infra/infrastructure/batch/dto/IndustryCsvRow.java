package SDD.smash.infra.infrastructure.batch.dto;

/** 업종 마스터 CSV 한 줄. */
public record IndustryCsvRow(String code, String name, String major) {
}
