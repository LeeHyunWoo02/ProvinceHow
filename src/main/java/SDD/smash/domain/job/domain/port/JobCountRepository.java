package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobCount;

import java.util.List;
import java.util.Optional;

/** 일자리 수 저장소 out-port. */
public interface JobCountRepository {

    /** 전 시군구의 일자리 총합 */
    List<RegionJobCount> findAllRegionTotals();

    /** 전 시군구의 특정 직종 일자리 수 */
    List<RegionJobCount> findAllRegionCountsOf(JobCode jobCode);

    /**
     * 해당 시군구의 일자리 총합.
     *
     * <p>집계 조회라 해당 시군구에 행이 하나도 없어도 결과가 없는 것이 아니라 <b>0</b> 이다.
     * As-Is 도 합계가 없으면 0 으로 바꿔 담아 "일자리 0개"로 응답했다.
     */
    long findTotalOf(SigunguCode sigunguCode);

    /** 해당 시군구의 특정 직종 일자리 수. 적재된 행이 없으면 비어 있다. */
    Optional<Long> findCountOf(SigunguCode sigunguCode, JobCode jobCode);
}
