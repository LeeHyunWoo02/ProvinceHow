package SDD.smash.domain.infra.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.domain.model.MajorInfraSummary;

import java.util.Optional;

/** 시군구·대분류별 인프라 총합·평균점수 저장소 out-port. */
public interface InfraMajorSummaryRepository {

    /** 해당 시군구에 이 대분류의 인프라가 적재돼 있지 않으면 비어 있다. */
    Optional<MajorInfraSummary> findBy(SigunguCode sigunguCode, Major major);
}
