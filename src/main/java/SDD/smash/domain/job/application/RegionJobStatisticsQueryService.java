package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.RegionJobStatisticsTrendPoint;
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
import java.util.TreeMap;

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

    /**
     * 최신월 한 시군구의 전 직종 대분류 통계. 적재 전이면 빈 리스트다.
     *
     * <p>지역 상세가 쓰는 경로다. 전국을 읽어 메모리에서 거르면 13행을 얻으려고 3,432행을
     * 읽게 되므로 조건을 쿼리로 내린다.
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionJobStatisticsView> getLatestStatisticsOfRegion(SigunguCode sigunguCode) {
        return regionJobStatisticsRepository.findLatestMonth()
                .map(month -> regionJobStatisticsRepository.findAllByMonthAndSigunguCode(month, sigunguCode))
                .orElseGet(List::of)
                .stream()
                .map(RegionJobStatisticsView::from)
                .toList();
    }

    /** 시군구 하나의 월별 시계열. 오래된 월이 앞에 온다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionJobStatisticsView> getSeriesOf(SigunguCode sigunguCode, JobCode jobCode) {
        return regionJobStatisticsRepository.findSeriesOf(sigunguCode, jobCode).stream()
                .map(RegionJobStatisticsView::from)
                .toList();
    }

    /**
     * 한 시군구의 직종 13종 합계를 월별로 접은 추세. 월 오름차순이며 최근 {@code months} 개월만 남긴다.
     *
     * <p>데이터가 있는 월만 방출한다(빈 월을 0 으로 채우지 않는다). 구인배수는 그 달 유효구직자수
     * 합계 기준으로 다시 계산하며, 합계가 0 이면 {@code null} 이다.
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionJobStatisticsTrendPoint> getRegionTrend(SigunguCode sigunguCode, int months) {

        // 월별로 유효구인인원·유효구직자수 합계를 접는다. TreeMap 이라 키(기준월)가 오름차순이다.
        Map<StatisticsMonth, long[]> byMonth = new TreeMap<>();
        for (RegionJobStatistics statistics : regionJobStatisticsRepository.findAllBySigunguCode(sigunguCode)) {
            long[] sum = byMonth.computeIfAbsent(statistics.month(), key -> new long[2]);
            sum[0] += statistics.validOpenings();
            sum[1] += statistics.validSeekers();
        }

        List<RegionJobStatisticsTrendPoint> ordered = byMonth.entrySet().stream()
                .map(entry -> toTrendPoint(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();

        // 최근 N개월만 남긴다. N 이 있는 월 수보다 크면 있는 만큼 그대로 준다.
        int from = Math.max(0, ordered.size() - months);
        return List.copyOf(ordered.subList(from, ordered.size()));
    }

    private RegionJobStatisticsTrendPoint toTrendPoint(StatisticsMonth month, long validOpenings, long validSeekers) {
        Double ratio = (validSeekers == 0L) ? null : (double) validOpenings / (double) validSeekers;
        return new RegionJobStatisticsTrendPoint(month.text(), validOpenings, validSeekers, ratio);
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
