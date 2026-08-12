package SDD.smash.domain.job.infrastructure.batch.dto;

/** 일자리 수 CSV 한 줄. */
public record JobCountCsvRow(String sigunguCode, String middleCode, Integer count) {
}
