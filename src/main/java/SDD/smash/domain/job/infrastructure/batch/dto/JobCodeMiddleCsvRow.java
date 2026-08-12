package SDD.smash.domain.job.infrastructure.batch.dto;

/** 직종 중분류 CSV 한 줄. {@code upstream} 은 소속 대분류 코드다. */
public record JobCodeMiddleCsvRow(String code, String name, String upstream) {
}
