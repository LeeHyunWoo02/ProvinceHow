package SDD.smash.job.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code job_code_middle} 테이블 매핑. 직종 중분류.
 *
 * <p>As-Is 의 {@code @ManyToOne JobCodeTop} 객체 참조를 {@code top_code} 값 컬럼으로 바꿨다.
 * 조인 컬럼 타입이 {@code JobCodeTop} 의 PK(varchar(255))에서 파생됐으므로
 * 여기서도 길이를 지정하지 않아야 같은 정의가 된다.
 */
@Entity
@Table(name = "job_code_middle")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JobCodeMiddleJpaEntity {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    /** 대분류를 객체가 아니라 코드로 참조한다. */
    @Column(name = "top_code", nullable = false)
    private String topCode;
}
