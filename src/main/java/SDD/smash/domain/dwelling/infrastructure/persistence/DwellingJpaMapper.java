package SDD.smash.domain.dwelling.infrastructure.persistence;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.dwelling.domain.model.DwellingMarket;
import SDD.smash.domain.dwelling.domain.model.RentStat;
import org.springframework.stereotype.Component;

/** 전월세 시세 도메인 모델 ↔ JPA 엔티티 변환. */
@Component
public class DwellingJpaMapper {

    public DwellingMarket toDomain(DwellingJpaEntity entity) {
        return DwellingMarket.reconstitute(
                SigunguCode.of(entity.getSigunguCode()),
                RentStat.of(entity.getMonthAvg(), entity.getMonthMid()),
                RentStat.of(entity.getJeonseAvg(), entity.getJeonseMid()));
    }

    public DwellingJpaEntity toJpaEntity(DwellingMarket market) {
        return DwellingJpaEntity.builder()
                .sigunguCode(market.sigunguCode().value())
                .monthAvg(market.monthly().average())
                .monthMid(market.monthly().median() == null ? null : market.monthly().median().manwon())
                .jeonseAvg(market.jeonse().average())
                .jeonseMid(market.jeonse().median() == null ? null : market.jeonse().median().manwon())
                .build();
    }
}
