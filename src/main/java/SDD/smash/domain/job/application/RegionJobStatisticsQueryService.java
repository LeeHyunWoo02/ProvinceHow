package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.RegionJobCount;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.RegionJobStatisticsKey;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.domain.port.RegionJobStatisticsRepository;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 고용행정통계 조회 유스케이스. job 컨텍스트의 공개 진입점이다.
 *
 * <p>기준월을 지정받지 않는다. EIS 는 월 1회 공표라 "현재 값" 은 언제나 <b>적재된 최신월</b>이고,
 * 그 월이 무엇인지는 결과 DTO 가 함께 들고 나간다.
 */
@Service
@RequiredArgsConstructor
public class RegionJobStatisticsQueryService {

    private final RegionJobStatisticsRepository regionJobStatisticsRepository;

    /** 적재된 최신 기준월({@code YYYY-MM}). 데이터가 없으면 비어 있다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public Optional<String> getLatestMonth() {
        return regionJobStatisticsRepository.findLatestMonth().map(StatisticsMonth::text);
    }

    /** 최신월의 전 시군구 통계. 직종을 지정하면 그 대분류만 본다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionJobStatisticsView> getLatestStatistics(JobCode jobCode) {
        return loadLatest(jobCode).stream()
                .map(RegionJobStatisticsView::from)
                .toList();
    }

    /** 최신월의 시군구 단건. 해당 조합이 적재돼 있지 않으면 비어 있다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public Optional<RegionJobStatisticsView> getLatestStatisticsOf(SigunguCode sigunguCode, JobCode jobCode) {
        return regionJobStatisticsRepository.findLatestMonth()
                .flatMap(month -> regionJobStatisticsRepository
                        .findOne(new RegionJobStatisticsKey(sigunguCode, jobCode, month)))
                .map(RegionJobStatisticsView::from);
    }

    /** 시군구 하나의 월별 시계열. 오래된 월이 앞에 온다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionJobStatisticsView> getSeriesOf(SigunguCode sigunguCode, JobCode jobCode) {
        return regionJobStatisticsRepository.findSeriesOf(sigunguCode, jobCode).stream()
                .map(RegionJobStatisticsView::from)
                .toList();
    }

    /**
     * 최신월의 유효구인인원을 {@link RegionJobCount} 로 투영한다.
     *
     * <p>{@code JobScorePolicy} 가 요구하는 형태가 (시군구, 개수) 뿐이라, 점수 산식을 건드리지 않고
     * 소스만 바꿔 끼울 수 있게 여기서 형태를 맞춘다. <b>구인배수는 여기에 섞지 않는다</b> —
     * 점수 산식 변경은 별도 결정 사항이다.
     *
     * @param jobCode {@code null} 이면 직종 전체를 시군구 단위로 합산한다
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionJobCount> getLatestValidOpenings(JobCode jobCode) {

        Map<SigunguCode, Long> totals = new LinkedHashMap<>();
        for (RegionJobStatistics statistics : loadLatest(jobCode)) {
            totals.merge(statistics.sigunguCode(), statistics.validOpenings(), Long::sum);
        }
        return totals.entrySet().stream()
                .map(entry -> new RegionJobCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<RegionJobStatistics> loadLatest(JobCode jobCode) {
        return regionJobStatisticsRepository.findLatestMonth()
                .map(month -> jobCode == null
                        ? regionJobStatisticsRepository.findAllByMonth(month)
                        : regionJobStatisticsRepository.findAllByMonthAndJobCode(month, jobCode))
                .orElseGet(List::of);
    }
}
