package SDD.smash.domain.dwelling.infrastructure.persistence;

import SDD.smash.domain.dwelling.domain.model.DwellingTypeStat;
import SDD.smash.domain.dwelling.domain.model.RentStat;
import org.springframework.stereotype.Component;

/** 주택유형별 시세 도메인 모델 ↔ JPA 엔티티 변환. */
@Component
public class DwellingByTypeJpaMapper {

    public DwellingTypeStat toDomain(DwellingByTypeJpaEntity entity) {
        return new DwellingTypeStat(
                entity.getHousingType(),
                RentStat.of(entity.getMonthAvg(), entity.getMonthMid()),
                RentStat.of(entity.getJeonseAvg(), entity.getJeonseMid()));
    }

    public DwellingByTypeJpaEntity toJpaEntity(String sigunguCode, DwellingTypeStat stat) {
        return DwellingByTypeJpaEntity.builder()
                .sigunguCode(sigunguCode)
                .housingType(stat.housingType())
                .monthAvg(stat.monthly().average())
                .monthMid(stat.monthly().medianManwon())
                .jeonseAvg(stat.jeonse().average())
                .jeonseMid(stat.jeonse().medianManwon())
                .build();
    }
}
