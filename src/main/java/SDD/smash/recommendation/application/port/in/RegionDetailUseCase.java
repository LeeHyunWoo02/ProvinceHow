package SDD.smash.recommendation.application.port.in;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.job.domain.model.JobCode;
import SDD.smash.recommendation.application.dto.RegionDetailInfo;

/** 지역 상세 조회 in-port. As-Is {@code DetailService.details} 자리다. */
public interface RegionDetailUseCase {

    /** {@code midJobCode} 는 선택 안 함이면 {@code null} 이다. */
    RegionDetailInfo details(SigunguCode sigunguCode, JobCode midJobCode);
}
