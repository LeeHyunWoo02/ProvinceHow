package SDD.smash.domain.dwelling.infrastructure.persistence;

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
 * {@code dwelling} 테이블 매핑.
 *
 * <p>As-Is 의 {@code @OneToOne Sigungu} 객체 참조를 {@code sigungu_code} 값 컬럼으로 바꿨다.
 * <b>{@code length = 5} 를 반드시 명시한다.</b> 빠뜨리면 varchar(255) 로 잡혀
 * {@code hbm2ddl.auto=update} 가 컬럼을 넓히므로 스키마가 바뀐다.
 *
 * <p>{@code unique = true} 도 유지해야 한다. As-Is 의 {@code @OneToOne} 조인 컬럼이
 * 유니크 인덱스를 만들었고, DwellingBatch 의 {@code ON DUPLICATE KEY UPDATE} 가 그 제약에 의존한다.
 * 없으면 배치 재실행 때 중복 행이 쌓인다.
 */
@Entity
@Table(name = "dwelling")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DwellingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 다른 Aggregate(Sigungu)를 객체가 아니라 코드로 참조한다. */
    @Column(name = "sigungu_code", length = 5, unique = true)
    private String sigunguCode;

    @Column(name = "month_avg")
    private Double monthAvg;

    @Column(name = "month_mid")
    private Integer monthMid;

    @Column(name = "jeonse_avg")
    private Double jeonseAvg;

    @Column(name = "jeonse_mid")
    private Integer jeonseMid;
}
