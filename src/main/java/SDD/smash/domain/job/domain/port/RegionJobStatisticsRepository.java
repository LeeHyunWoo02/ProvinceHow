package SDD.smash.domain.job.domain.port;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.List;
import java.util.Optional;

/** 고용행정통계 저장소 out-port. 조회는 화면이 실제로 쓰는 만큼만 연다. */
public interface RegionJobStatisticsRepository {

    /** 적재된 것 중 가장 최근 기준월. 한 건도 없으면 비어 있다. */
    Optional<StatisticsMonth> findLatestMonth();

    /** 해당 월의 전 시군구 × 전 직종 통계 */
    List<RegionJobStatistics> findAllByMonth(StatisticsMonth month);

    /** 해당 월의 특정 직종 통계 */
    List<RegionJobStatistics> findAllByMonthAndJobCode(StatisticsMonth month, JobCode jobCode);

    /** 해당 월의 특정 시군구 통계(전 직종 대분류). 지역 상세가 쓰는 경로다. */
    List<RegionJobStatistics> findAllByMonthAndSigunguCode(StatisticsMonth month, SigunguCode sigunguCode);

    /** 단건 조회 */
    Optional<RegionJobStatistics> findOne(RegionJobStatisticsKey key);

    /** 시군구 하나의 월별 시계열. 오래된 월부터 정렬한다. */
    List<RegionJobStatistics> findSeriesOf(SigunguCode sigunguCode, JobCode jobCode);
}
