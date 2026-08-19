package SDD.smash.domain.infra.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code infra_staging_count} — 수집 결과인 (시군구, 업종) 시설 수. <b>아직 서비스에 노출되지 않는다.</b>
 *
 * <p>{@code infra} 테이블과 컬럼 구성이 다르다. staging 에는 {@code ratio}/{@code score} 가 없다 —
 * 두 값은 스냅샷 <b>전체</b>를 봐야 계산되므로 회차가 완성된 뒤 {@code InfraStatPolicy} 가 한 번에 낸다.
 * 부분 수집분으로 계산한 백분위는 모집단이 달라 의미가 없다.
 *
 * <h2>합산 upsert 다</h2>
 * 한 (시군구, 업종) 조합에 <b>여러 대상이 기여</b>한다. 일반구 재분배 때문에 대상 하나가
 * 여러 시군구를 만들고, 반대로 여러 대상이 같은 시군구로 모이기도 한다. 그래서 쓰기는
 * {@code ON DUPLICATE KEY UPDATE facility_count = facility_count + VALUES(facility_count)} 다.
 *
 * <p>이중 합산은 {@link InfraCollectionTargetJpaEntity} 가 막는다 — 이미 진행 행이 있는 대상은
 * 다시 수집되지 않고, 진행 행과 카운트 행이 같은 트랜잭션에서 커밋되기 때문이다.
 */
@Entity
@Table(
        name = "infra_staging_count",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_infra_staging_count",
                        columnNames = {"run_key", "sigungu_code", "industry_code"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class InfraStagingCountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 수집 회차 키. {@link InfraCollectionTargetJpaEntity#getRunKey()} 와 같은 값이다. */
    @Column(name = "run_key", length = 20, nullable = false)
    private String runKey;

    @Column(name = "sigungu_code", length = 5, nullable = false)
    private String sigunguCode;

    @Column(name = "industry_code", length = 10, nullable = false)
    private String industryCode;

    /** 누적 시설 수. 이름을 {@code count} 로 하지 않는다 — MySQL 예약어 백틱 이스케이프를 피한다. */
    @Column(name = "facility_count", nullable = false)
    private Integer facilityCount;
}
