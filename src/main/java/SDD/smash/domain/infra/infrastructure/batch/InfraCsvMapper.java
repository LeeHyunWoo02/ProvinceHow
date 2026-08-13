package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.infrastructure.batch.dto.IndustryCsvRow;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.persistence.IndustryJpaEntity;

import static SDD.smash.global.util.BatchTextUtil.normalize;

/** 업종 마스터/레거시 CSV 행 → JPA 엔티티 변환. */
public final class InfraCsvMapper {

    private InfraCsvMapper() {
    }

    /**
     * 업종 마스터 한 줄을 {@code industry} 행으로 바꾼다. <b>현재 사용하는 경로다.</b>
     *
     * <p>{@code major} 가 비어 있는 항목은 여기까지 오지 않는다
     * ({@code IndustryMaster.active()} 가 걸러낸다).
     */
    public static IndustryJpaEntity toIndustryJpaEntity(IndustryMasterEntry entry) {
        return IndustryJpaEntity.builder()
                .code(entry.code().value())
                .name(entry.name())
                .major(entry.major())
                .build();
    }

    /**
     * 레거시 {@code industry.csv}({@code code,name,major}) 한 줄 변환.
     *
     * @deprecated 업종 마스터가 {@code infra/industry-master.yml} 로 옮겨져 더 이상 쓰이지 않는다.
     *             {@code Major.valueOf} 가 오타 하나에 배치 전체를 죽이던 경로이기도 하다.
     */
    @Deprecated(forRemoval = false)
    public static IndustryJpaEntity toIndustryJpaEntity(IndustryCsvRow row) {
        return IndustryJpaEntity.builder()
                .code(normalize(row.code()))
                .name(normalize(row.name()))
                .major(Major.valueOf(normalize(row.major())))
                .build();
    }
}
