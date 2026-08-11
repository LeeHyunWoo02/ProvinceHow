package SDD.smash.infra.infrastructure.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 인프라 Upsert 파라미터.
 *
 * <p><b>필드명이 곧 SQL 네임드 파라미터명</b>이다. {@code BeanPropertyItemSqlParameterSourceProvider}
 * 가 getter 로 읽으므로 record 가 아니라 getter 를 갖는 클래스여야 한다.
 *
 * <p>{@code count} 가 {@code String} 인 것은 As-Is {@code InfraUpsertDTO} 그대로다
 * (DB 컬럼은 Integer 이지만 JDBC 가 숫자 문자열을 암묵 변환하는 데 의존한다).
 * 고쳐야 할 As-Is 특이사항으로 보고하되 이관에서는 손대지 않는다.
 */
@Getter
@Builder
public class InfraUpsertRow {

    private String sigunguCode;
    private String industryCode;
    private String count;
    private BigDecimal ratio;
    private BigDecimal score;
}
