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
 * {@code sido} 테이블 매핑.
 *
 * <p>테이블명·컬럼명은 As-Is {@code SDD.smash.Address.Entity.Sido} 와 동일하다.
 * {@code hbm2ddl.auto=update} 이므로 이름이 달라지면 새 테이블이 생겨 데이터가 갈린다.
 *
 * <p>As-Is 의 {@code @OneToMany List<Sigungu>} 는 옮기지 않는다.
 * 시군구는 별도 Aggregate 이며 물리 컬럼도 만들지 않던 매핑이라 스키마 영향이 없다.
 */
@Entity
@Table(name = "sido")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SidoJpaEntity {

    @Id
    @Column(name = "sido_code", length = 2)
    private String sidoCode;

    @Column(name = "name", nullable = false)
    private String name;
}
