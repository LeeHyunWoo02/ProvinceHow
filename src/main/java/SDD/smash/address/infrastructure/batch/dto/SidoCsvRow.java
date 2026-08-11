package SDD.smash.address.infrastructure.batch.dto;

/** 시도 CSV 한 줄. 기술 DTO 이므로 도메인 밖으로 나가지 않는다. */
public record SidoCsvRow(String sidoCode, String name) {
}
