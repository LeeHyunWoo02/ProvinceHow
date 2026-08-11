package SDD.smash.infra.infrastructure.persistence;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.infra.domain.model.IndustryCount;
import SDD.smash.infra.domain.model.MajorInfraSummary;
import SDD.smash.infra.domain.model.RegionMajorScore;
import SDD.smash.infra.infrastructure.persistence.projection.IndustryCountRow;
import SDD.smash.infra.infrastructure.persistence.projection.MajorInfraSummaryRow;
import SDD.smash.infra.infrastructure.persistence.projection.RegionMajorScoreRow;
import org.springframework.stereotype.Component;

/** infra 도메인 모델 ↔ JPA 프로젝션 변환. */
@Component
public class InfraJpaMapper {

    public MajorInfraSummary toDomain(MajorInfraSummaryRow row) {
        long count = row.count() == null ? 0L : row.count();
        return new MajorInfraSummary(row.major(), count, row.averageScore());
    }

    public IndustryCount toDomain(IndustryCountRow row) {
        int count = row.count() == null ? 0 : row.count();
        return new IndustryCount(row.major(), row.industryName(), count, row.ratio());
    }

    public RegionMajorScore toDomain(RegionMajorScoreRow row) {
        return new RegionMajorScore(SigunguCode.of(row.sigunguCode()), row.major(), row.averageScore());
    }
}
