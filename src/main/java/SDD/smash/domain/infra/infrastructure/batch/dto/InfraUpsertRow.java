package SDD.smash.domain.infra.infrastructure.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 인프라 Upsert 파라미터.
 *
 * <p><b>필드명이 곧 SQL 네임드 파라미터명</b>이다. {@code BeanPropertyItemSqlParameterSourceProvider}
 * 가 getter 로 읽으므로 record 가 아니라 getter 를 갖는 클래스여야 한다.
 *
 * <p>{@code count} 는 {@code Integer} 다. As-Is 는 {@code String} 이라 JDBC 가 숫자 문자열을
 * INT 컬럼으로 암묵 변환하는 데 의존했다 — 값이 숫자가 아니면 적재 시점에야 드러나고,
 * 드라이버 설정에 따라 동작이 달라진다. 타입으로 못 박는다.
 */
@Getter
@Builder
public class InfraUpsertRow {

    private String sigunguCode;
    private String industryCode;
    private Integer count;
    private BigDecimal ratio;
    private BigDecimal score;
}
