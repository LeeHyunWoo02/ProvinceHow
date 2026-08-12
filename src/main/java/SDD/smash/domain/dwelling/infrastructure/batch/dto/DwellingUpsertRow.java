package SDD.smash.domain.dwelling.infrastructure.batch.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 전월세 Upsert 파라미터.
 *
 * <p><b>필드명이 곧 SQL 네임드 파라미터명</b>이다({@code :sigunguCode}, {@code :monthAvg} ...).
 * {@code BeanPropertyItemSqlParameterSourceProvider} 가 getter 로 읽으므로
 * record 가 아니라 getter 를 갖는 클래스여야 한다. 한쪽만 바꾸면 런타임에 깨진다.
 */
@Getter
@Builder
public class DwellingUpsertRow {

    private String sigunguCode;

    private Double monthAvg;
    private Integer monthMid;

    private Double jeonseAvg;
    private Integer jeonseMid;
}
