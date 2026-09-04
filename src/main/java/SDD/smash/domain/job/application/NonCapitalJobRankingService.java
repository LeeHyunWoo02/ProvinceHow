package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.NonCapitalRankView;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.NonCapitalRatioSnapshot;
import SDD.smash.domain.job.domain.model.RegionJobStatistics;
import SDD.smash.domain.job.domain.model.StatisticsMonth;
import SDD.smash.domain.job.domain.port.NonCapitalRatioCache;
import SDD.smash.domain.job.domain.port.RegionJobStatisticsRepository;
import SDD.smash.domain.job.domain.service.NonCapitalRankingPolicy;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 비수도권 내 구인배수 순위 유스케이스. job 컨텍스트의 공개 진입점이다.
 *
 * <p>백분위는 모집단(최신월 비수도권 전체)이 있어야 나오므로, 매 요청 전국을 훑지 않도록
 * 접어 둔 분포를 캐시 포트 뒤에서 재사용한다. 조합·계산 규칙은 전부
 * {@code NonCapitalRankingPolicy} 에 있고 여기는 오케스트레이션만 한다.
 *
 * <p>공개 메서드에는 {@code @Transactional} 을 붙이지 않는다 — 캐시 접근을 포함하는 경로라
 * 트랜잭션으로 감싸면 커넥션을 쥔 채 캐시를 기다리게 된다. DB 조회부만 잘라 읽기 트랜잭션으로
 * 감싼다({@code JobScoreService} 와 같은 판단).
 */
@Service
@RequiredArgsConstructor
public class NonCapitalJobRankingService {

    private final RegionJobStatisticsRepository regionJobStatisticsRepository;
    private final NonCapitalRatioCache nonCapitalRatioCache;

    private final NonCapitalRankingPolicy policy = new NonCapitalRankingPolicy();

    /**
     * 직종 13종 합계 구인배수의 비수도권 백분위. 수도권 지역이거나 구인배수를 계산할 수 없으면
     * (유효구직자수 0) 비어 있다.
     */
    public Optional<NonCapitalRankView> getRegionRank(SigunguCode sigunguCode) {
        return snapshot()
                .flatMap(snapshot -> policy.rankOf(sigunguCode, snapshot.totalRatios()))
                .map(NonCapitalRankView::from);
    }

    /**
     * 한 시군구의 직종 대분류별 비수도권 백분위. 키는 직종 대분류 코드다.
     * 수도권 지역이면 빈 맵이다.
     */
    public Map<String, NonCapitalRankView> getRegionRankByJob(SigunguCode sigunguCode) {

        Map<String, NonCapitalRankView> ranks = new LinkedHashMap<>();
        Optional<NonCapitalRatioSnapshot> loaded = snapshot();
        if (loaded.isEmpty() || sigunguCode == null) {
            return ranks;
        }

        NonCapitalRatioSnapshot snapshot = loaded.get();
        for (JobCode jobCode : snapshot.jobCodes()) {
            policy.rankOf(sigunguCode, snapshot.ratiosOf(jobCode))
                    .ifPresent(rank -> ranks.put(jobCode.value(), NonCapitalRankView.from(rank)));
        }
        return ranks;
    }

    /**
     * 비수도권 전 시군구의 합계 구인배수 백분위. 추천 점수 정규화가 쓰는 경로다.
     * 수도권 시군구는 애초에 모집단이 아니므로 키에 없다.
     */
    public Map<SigunguCode, Integer> getNonCapitalPercentiles() {
        return snapshot()
                .map(snapshot -> policy.percentiles(snapshot.totalRatios()))
                .orElseGet(Map::of);
    }

    /**
     * 최신월 분포. 캐시에 있으면 그대로 쓰고, 없으면 그 달 전국을 한 번 읽어 접는다.
     *
     * <p>두 DB 조회 사이에 캐시 확인이 끼어 있어 하나의 트랜잭션으로 묶을 수 없다(캐시를 트랜잭션
     * 안에서 기다리게 된다). 그래서 조회 두 개를 각각 읽기 트랜잭션 메서드로 분리한다.
     */
    private Optional<NonCapitalRatioSnapshot> snapshot() {

        Optional<StatisticsMonth> latest = loadLatestMonth();
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        StatisticsMonth month = latest.get();

        Optional<NonCapitalRatioSnapshot> cached = nonCapitalRatioCache.find(month);
        if (cached.isPresent()) {
            return cached;
        }

        NonCapitalRatioSnapshot snapshot = policy.snapshot(month, loadMonthRows(month));
        nonCapitalRatioCache.put(snapshot);
        return Optional.of(snapshot);
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    protected Optional<StatisticsMonth> loadLatestMonth() {
        return regionJobStatisticsRepository.findLatestMonth();
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    protected List<RegionJobStatistics> loadMonthRows(StatisticsMonth month) {
        return regionJobStatisticsRepository.findAllByMonth(month);
    }
}
