package SDD.smash.domain.address.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code population} 테이블 매핑.
 *
 * <p>As-Is 의 {@code @OneToOne Sigungu} 객체 참조를 {@code sigungu_code} 값 컬럼으로 바꿨다.
 * 컬럼명·타입(varchar(5))·유니크 제약이 As-Is 조인 컬럼과 같아 스키마 변경이 없다.
 *
 * <p>{@code unique = true} 는 반드시 유지해야 한다.
 * PopulationBatch 가 {@code ON DUPLICATE KEY UPDATE} 로 upsert 하므로
 * 이 유니크 제약이 없으면 재실행 때 중복 행이 쌓인다.
 */
@Entity
@Table(name = "population")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PopulationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sigungu_code", length = 5, nullable = false, unique = true)
    private String sigunguCode;

    @Column(name = "population_count", nullable = false)
    private Integer populationCount;
}
