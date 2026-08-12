package SDD.smash.domain.infra.infrastructure.persistence;

import SDD.smash.domain.infra.domain.model.Major;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code industry} 테이블 매핑. 업종 마스터.
 *
 * <p>As-Is {@code Industry} 는 {@code @Table} 이 없어 Hibernate 기본 명명으로
 * {@code industry} 가 됐다. 그 이름을 명시해 고정한다.
 *
 * <p>{@code Major} 는 순수 Java enum(프레임워크 의존 없음)이라 JPA 엔티티가 도메인의
 * 이 enum 을 직접 써도 분리 원칙을 어기지 않는다(persistence-conventions §2.4 예시와 동일).
 */
@Entity
@Table(name = "industry")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IndustryJpaEntity {

    @Id
    @Column(name = "industry_code", length = 10, nullable = false)
    private String code;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "major")
    private Major major;
}
