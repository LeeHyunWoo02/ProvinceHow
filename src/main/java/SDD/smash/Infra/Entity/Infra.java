package SDD.smash.Infra.Entity;

import SDD.smash.Address.Entity.Sigungu;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

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
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Infra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(
            name = "sigungu_code",
            foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT)
    )
    private Sigungu sigungu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "industry_code",
            foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT)
    )
    private Industry industry;

    @Column(name = "`count`", nullable = false)
    @NotNull
    private Integer count;

    @Column(name = "ratio", precision = 18, scale = 2, nullable = false)
    @NotNull
    private BigDecimal ratio;

    @Column(name = "score", precision = 6, scale = 2, nullable = false)
    @NotNull
    private BigDecimal score;
}
