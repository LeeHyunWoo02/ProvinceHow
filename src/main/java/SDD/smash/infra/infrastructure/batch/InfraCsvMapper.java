package SDD.smash.infra.infrastructure.batch;

import SDD.smash.infra.domain.model.Major;
import SDD.smash.infra.infrastructure.batch.dto.IndustryCsvRow;
import SDD.smash.infra.infrastructure.persistence.IndustryJpaEntity;

import static SDD.smash.Util.BatchTextUtil.normalize;

/**
 * 업종 CSV 행 → JPA 엔티티 변환. As-Is {@code InfraConverter} 를 옮긴 것이다.
 */
public final class InfraCsvMapper {

    private InfraCsvMapper() {
    }

    public static IndustryJpaEntity toIndustryJpaEntity(IndustryCsvRow row) {
        return IndustryJpaEntity.builder()
                .code(normalize(row.code()))
                .name(normalize(row.name()))
                .major(Major.valueOf(normalize(row.major())))
                .build();
    }
}
