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
 * {@code job_code_top} 테이블 매핑. 직종 대분류.
 *
 * <p>As-Is {@code JobCodeTop} 은 {@code @Table} 이 없어 Hibernate 기본 명명으로
 * {@code job_code_top} 이 됐다. 그 이름을 명시해 고정한다.
 *
 * <p><b>{@code code}/{@code name} 에 {@code length} 를 지정하지 않는다.</b>
 * As-Is 도 지정하지 않아 varchar(255) 로 만들어졌다. 여기서 길이를 좁히면
 * {@code hbm2ddl.auto=update} 가 컬럼 정의를 바꾸려 든다.
 */
@Entity
@Table(name = "job_code_top")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class JobCodeTopJpaEntity {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;
}
