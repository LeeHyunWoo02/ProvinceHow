package SDD.smash.domain.dwelling.infrastructure.persistence;

import SDD.smash.domain.dwelling.domain.model.HousingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * {@code dwelling_by_type} 테이블 매핑 — 시군구 × 주택유형 단위의 시세.
 *
 * <p>{@code dwelling} 은 시군구당 1행(3종 풀링 통합 평균)이라 유형별 행을 담을 수 없다.
 * 기존 유니크 제약을 복합키로 바꾸는 건 {@code hbm2ddl.auto=update} 가 반영하지 못하므로 신규 테이블로 분리했다.
 *
 * <p>복합 유니크는 배치의 {@code ON DUPLICATE KEY UPDATE} upsert 가 의존한다. 없으면 재실행 때 중복 행이 쌓인다.
 * 선두 컬럼이 {@code sigungu_code} 라 시군구 단독 조회도 leftmost prefix 로 커버되므로 별도 인덱스를 두지 않는다.
 * {@code length} 를 빠뜨리면 varchar(255) 로 잡혀 스키마가 달라지니 반드시 명시한다.
 */
@Entity
@Table(name = "dwelling_by_type",
        uniqueConstraints = @UniqueConstraint(name = "uk_dwelling_by_type_sigungu_housing",
                columnNames = {"sigungu_code", "housing_type"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DwellingByTypeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 다른 Aggregate(Sigungu)를 객체가 아니라 코드로 참조한다. */
    @Column(name = "sigungu_code", length = 5, nullable = false)
    private String sigunguCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", length = 30, nullable = false)
    private HousingType housingType;

    @Column(name = "month_avg")
    private Double monthAvg;

    @Column(name = "month_mid")
    private Integer monthMid;

    @Column(name = "jeonse_avg")
    private Double jeonseAvg;

    @Column(name = "jeonse_mid")
    private Integer jeonseMid;
}
