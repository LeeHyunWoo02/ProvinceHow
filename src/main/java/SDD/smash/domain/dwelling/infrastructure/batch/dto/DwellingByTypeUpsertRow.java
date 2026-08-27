package SDD.smash.domain.dwelling.infrastructure.batch.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 주택유형별 전월세 Upsert 파라미터.
 *
 * <p>{@link DwellingUpsertRow} 와 같은 제약이다 — 필드명이 곧 SQL 네임드 파라미터명이고
 * {@code BeanPropertyItemSqlParameterSourceProvider} 가 getter 로 읽으므로 record 가 아니다.
 *
 * <p>{@code housingType} 이 enum 이 아니라 String 인 이유: JPA 가 아니라 JDBC 로 직접 쓰므로
 * MySQL ENUM 컬럼에 넣을 값은 {@code HousingType.name()} 문자열이어야 한다.
 */
@Getter
@Builder
public class DwellingByTypeUpsertRow {

    private String sigunguCode;

    private String housingType;

    private Double monthAvg;
    private Integer monthMid;

    private Double jeonseAvg;
    private Integer jeonseMid;
}
