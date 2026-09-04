package SDD.smash.domain.infra.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.domain.model.MajorInfraSummary;

import java.util.List;
import java.util.Optional;

/** 시군구·대분류별 인프라 총합·평균점수 저장소 out-port. */
public interface InfraMajorSummaryRepository {

    /** 해당 시군구에 이 대분류의 인프라가 적재돼 있지 않으면 비어 있다. */
    Optional<MajorInfraSummary> findBy(SigunguCode sigunguCode, Major major);

    /**
     * 해당 시군구의 대분류별 요약을 한 번에 조회한다. 데이터가 있는 대분류만 담겨 오며,
     * 없는 대분류는 결과에 나타나지 않는다(단건 {@link #findBy} 의 empty 에 대응한다).
     */
    List<MajorInfraSummary> findAllBy(SigunguCode sigunguCode);
}
