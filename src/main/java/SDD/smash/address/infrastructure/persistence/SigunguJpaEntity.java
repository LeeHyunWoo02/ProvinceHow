package SDD.smash.address.infrastructure.persistence;

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
 * {@code sigungu} 테이블 매핑.
 *
 * <p>As-Is 의 {@code @ManyToOne Sido} 객체 참조를 {@code sido_code} 값 컬럼으로 바꿨다.
 * <b>컬럼명과 타입(varchar(2))이 As-Is 조인 컬럼과 같아 스키마 변경이 없다.</b>
 * DB 에 이미 걸려 있는 물리 FK 제약은 그대로 남는다. 제거는 전환 완료 후 별도 DDL 로 한다.
 */
@Entity
@Table(name = "sigungu")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SigunguJpaEntity {

    @Id
    @Column(name = "sigungu_code", length = 5)
    private String sigunguCode;

    @Column(name = "name", nullable = false)
    private String name;

    /** 다른 Aggregate(Sido)를 객체가 아니라 코드로 참조한다. */
    @Column(name = "sido_code", length = 2, nullable = false)
    private String sidoCode;
}
