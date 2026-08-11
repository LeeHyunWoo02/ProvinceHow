package SDD.smash.infra.infrastructure.batch.dto;

import java.math.BigDecimal;

/**
 * 인프라 CSV 한 줄.
 *
 * <p>세 번째 컬럼 헤더는 {@code count} 지만, 값은 문자열로 읽는다({@code countRaw}) —
 * As-Is {@code InfraDTO} 도 이 필드를 {@code String} 으로 들고 있었다. Upsert 시 JDBC 가
 * 숫자 문자열을 INT 컬럼으로 암묵 변환하는 것에 의존하는 As-Is 특성을 그대로 옮긴 것이다.
 */
public record InfraCsvRow(String sigunguCode, String industryCode, String countRaw,
                          BigDecimal ratio, BigDecimal score) {
}
