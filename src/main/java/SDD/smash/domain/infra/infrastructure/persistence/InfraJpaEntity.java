package SDD.smash.domain.infra.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * {@code infra} 테이블 매핑. 시군구 × 업종 팩트 테이블.
 *
 * <p>As-Is 의 {@code @ManyToOne Sigungu} / {@code @ManyToOne Industry} 두 객체 참조를
 * 값 컬럼으로 바꿨다.
 * <ul>
 *   <li>{@code sigungu_code} — Sigungu PK 에서 파생돼 varchar(5) 였으므로 {@code length = 5}</li>
 *   <li>{@code industry_code} — Industry PK 가 {@code length = 10} 이었으므로 그 길이를 따른다</li>
 * </ul>
 *
 * <p>제약·인덱스에 <b>이미 이름이 있으므로 그대로 유지한다</b>
 * ({@code uk_infra_sigungu_industry}, {@code idx_infra_sigungu}, {@code idx_infra_industry}).
 * 이름을 빼거나 바꾸면 기존과 다른 이름의 인덱스가 새로 생긴다.
 */
@Entity
@Table(
        name = "infra",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_infra_sigungu_industry",
                        columnNames = {"sigungu_code", "industry_code"})
        },
        indexes = {
                @Index(name = "idx_infra_sigungu", columnList = "sigungu_code"),
                @Index(name = "idx_infra_industry", columnList = "industry_code")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class InfraJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sigungu_code", length = 5, nullable = false)
    private String sigunguCode;

    @Column(name = "industry_code", length = 10, nullable = false)
    private String industryCode;

    @Column(name = "`count`", nullable = false)
    private Integer count;

    @Column(name = "ratio", precision = 18, scale = 2, nullable = false)
    private BigDecimal ratio;

    @Column(name = "score", precision = 6, scale = 2, nullable = false)
    private BigDecimal score;
}
