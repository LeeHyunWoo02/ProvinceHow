package SDD.smash.domain.address.infrastructure.persistence;

import SDD.smash.domain.address.domain.model.Sido;
import SDD.smash.global.domain.model.SidoCode;
import org.springframework.stereotype.Component;

/** 시도 도메인 모델 ↔ JPA 엔티티 변환. */
@Component
public class SidoJpaMapper {

    public Sido toDomain(SidoJpaEntity entity) {
        return Sido.reconstitute(SidoCode.of(entity.getSidoCode()), entity.getName());
    }
}
