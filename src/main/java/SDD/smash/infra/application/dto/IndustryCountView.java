package SDD.smash.infra.application.dto;

import SDD.smash.infra.domain.model.IndustryCount;
import SDD.smash.infra.domain.model.Major;

import java.math.BigDecimal;

/** 업종별 인프라 상세 한 건. As-Is {@code InfraDetails} 자리를 대신한다. */
public record IndustryCountView(Major major, String industryName, int count, BigDecimal ratio) {

    public static IndustryCountView from(IndustryCount count) {
        return new IndustryCountView(count.major(), count.industryName(), count.count(), count.ratio());
    }
}
