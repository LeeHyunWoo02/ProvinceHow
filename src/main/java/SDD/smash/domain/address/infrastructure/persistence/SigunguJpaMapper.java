package SDD.smash.domain.address.infrastructure.persistence;

import SDD.smash.domain.address.domain.model.Population;
import SDD.smash.domain.address.domain.model.RegionCode;
import SDD.smash.domain.address.domain.model.Sigungu;
import SDD.smash.domain.address.infrastructure.persistence.projection.RegionCodeRow;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;
import org.springframework.stereotype.Component;

/** 시군구 Aggregate(및 그 안의 인구) 도메인 모델 ↔ JPA 엔티티 변환. */
@Component
public class SigunguJpaMapper {

    public Sigungu toDomain(SigunguJpaEntity entity) {
        return Sigungu.reconstitute(
                SigunguCode.of(entity.getSigunguCode()),
                entity.getName(),
                SidoCode.of(entity.getSidoCode()));
    }

    public SigunguJpaEntity toJpaEntity(Sigungu sigungu) {
        return SigunguJpaEntity.builder()
                .sigunguCode(sigungu.code().value())
                .name(sigungu.name())
                .sidoCode(sigungu.sidoCode().value())
                .build();
    }

    public Population toPopulation(PopulationJpaEntity entity) {
        return Population.of(SigunguCode.of(entity.getSigunguCode()), entity.getPopulationCount());
    }

    public RegionCode toRegionCode(RegionCodeRow row) {
        return new RegionCode(
                SidoCode.of(row.sidoCode()),
                row.sidoName(),
                SigunguCode.of(row.sigunguCode()),
                row.sigunguName());
    }
}
