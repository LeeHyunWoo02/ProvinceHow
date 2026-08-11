package SDD.smash.infra.application.port.in;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.infra.application.dto.IndustryCountView;
import SDD.smash.infra.application.dto.MajorInfraSummaryView;

import java.util.List;

/**
 * 인프라 조회 in-port. {@code recommendation} 이 infra 를 호출하는 통로다.
 */
public interface InfraQueryUseCase {

    /** 해당 시군구의 대분류(4종)별 인프라 개수·평균점수. 데이터가 없는 대분류는 목록에서 빠진다. */
    List<MajorInfraSummaryView> getMajorInfraSummaries(SigunguCode sigunguCode);

    /** 해당 시군구의 업종별(14종) 인프라 상세 목록. 적재된 것이 없으면 빈 목록이다. */
    List<IndustryCountView> getInfraDetails(SigunguCode sigunguCode);
}
