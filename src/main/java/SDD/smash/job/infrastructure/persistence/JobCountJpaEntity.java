package SDD.smash.job.infrastructure.persistence;

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
 * {@code JobCount} 테이블 매핑. 시군구 × 직종중분류의 일자리 수.
 *
 * <p>As-Is 의 {@code @ManyToOne Sigungu} / {@code @ManyToOne JobCodeMiddle} 두 객체 참조를
 * 값 컬럼으로 바꿨다. 컬럼 정의를 As-Is 와 똑같이 맞춘다.
 * <ul>
 *   <li>{@code sigungu_code} — Sigungu PK 에서 파생돼 varchar(5) 였으므로 {@code length = 5}</li>
 *   <li>{@code job_code_middle_code} — JobCodeMiddle PK 에서 파생돼 varchar(255) 였으므로 길이 미지정</li>
 * </ul>
 *
 * <p>유니크 제약에 <b>이름을 주지 않는다.</b> As-Is 도 이름 없이 선언해 Hibernate 가
 * 테이블·컬럼 해시로 이름을 만들었고, 같은 방식으로 선언해야 같은 제약으로 수렴한다.
 * 이 제약은 JobCountBatch 의 {@code ON DUPLICATE KEY UPDATE} 가 의존하므로 반드시 유지해야 한다.
 */
@Entity
@Table(
        name = "JobCount",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"sigungu_code", "job_code_middle_code"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JobCountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sigungu_code", length = 5, nullable = false)
    private String sigunguCode;

    @Column(name = "job_code_middle_code", nullable = false)
    private String jobCodeMiddleCode;

    @Column(name = "count", nullable = false)
    private Integer count;
}
