package SDD.smash.infra.application.dto;

import SDD.smash.infra.domain.model.Major;
import SDD.smash.infra.domain.model.MajorInfraSummary;

/** 인프라 대분류별 요약. As-Is {@code InfraMajor} 자리를 대신한다. */
public record MajorInfraSummaryView(Major major, long count, Double averageScore) {

    public static MajorInfraSummaryView from(MajorInfraSummary summary) {
        return new MajorInfraSummaryView(summary.major(), summary.count(), summary.averageScore());
    }
}
