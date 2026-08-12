package SDD.smash.domain.job.infrastructure.persistence;

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
 *
 * <p><b>{@code job_code_middle_code} 에만 인덱스를 명시하는 이유</b> — persistence-conventions §2.5.
 * <ul>
 *   <li>{@code sigungu_code} 는 위 복합 유니크의 <b>선두 컬럼</b>이라 leftmost prefix 로 커버된다
 *       ({@code sumCountBySigunguCode}, {@code GROUP BY j.sigunguCode}).
 *       따로 인덱스를 선언하면 중복이다.</li>
 *   <li>{@code job_code_middle_code} 는 복합 유니크의 <b>두 번째</b> 컬럼이라
 *       단독 조회에 그 인덱스를 쓸 수 없다. {@code findAllRegionCountsOf} 가
 *       {@code WHERE j.jobCodeMiddleCode = :jobCode} 로 단독 필터하므로 인덱스가 필요하다.</li>
 * </ul>
 * 기존 DB 의 옛 FK 인덱스 이름 정리는
 * {@code docker/mysql/ddl/2026-08-11-rename-fk-index.sql} 를 참고한다.
 */
@Entity
@Table(
        name = "JobCount",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"sigungu_code", "job_code_middle_code"})
        },
        indexes = {
                @Index(name = "idx_jobcount_job_code_middle", columnList = "job_code_middle_code")
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
