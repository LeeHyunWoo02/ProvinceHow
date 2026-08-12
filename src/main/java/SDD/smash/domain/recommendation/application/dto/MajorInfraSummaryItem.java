package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.infra.application.dto.MajorInfraSummaryView;
import SDD.smash.domain.infra.domain.model.Major;

/** 인프라 대분류별 요약. As-Is {@code InfraMajor} 자리를 대신한다(필드명 {@code num}/{@code score} 그대로). */
public record MajorInfraSummaryItem(Major major, Long num, Double score) {

    public static MajorInfraSummaryItem from(MajorInfraSummaryView view) {
        return new MajorInfraSummaryItem(view.major(), view.count(), view.averageScore());
    }
}
