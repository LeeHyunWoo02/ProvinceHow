package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.infra.application.dto.IndustryCountView;
import SDD.smash.domain.infra.domain.model.Major;

import java.math.BigDecimal;

/** 업종별 인프라 상세 한 건. As-Is {@code InfraDetails} 자리를 대신한다(필드명 {@code name}/{@code num} 그대로). */
public record IndustryDetailItem(Major major, String name, Integer num, BigDecimal ratio) {

    public static IndustryDetailItem from(IndustryCountView view) {
        return new IndustryDetailItem(view.major(), view.industryName(), view.count(), view.ratio());
    }
}
