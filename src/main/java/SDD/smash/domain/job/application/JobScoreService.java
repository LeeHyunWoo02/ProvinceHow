package SDD.smash.domain.job.application;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobScoreKey;
import SDD.smash.domain.job.domain.model.RegionJobCount;
import SDD.smash.domain.job.domain.port.JobCategoryRepository;
import SDD.smash.domain.job.domain.port.JobCountRepository;
import SDD.smash.domain.job.domain.port.JobScoreCache;
import SDD.smash.domain.job.domain.service.JobScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일자리 적합도 점수 유스케이스. As-Is {@code JobScoreService} 의
 * <b>오케스트레이션 부분만</b> 옮긴 것이다(검증 → 캐시 확인 → 조회 → 정책 적용 → 캐시 저장).
 *
 * <p>점수 공식은 {@code JobScorePolicy}, Redis 상세는 {@code JobScoreRedisAdapter} 로 빠졌다.
 *
 * <p>점수의 입력은 두 가지다 — 일자리 수(주)와 비수도권 내 구인배수 백분위(보조).
 * 구인배수는 <b>정규화된 백분위</b>로만 들어간다. 원시 배수를 그대로 넣으면 이상치가 상위를
 * 차지해 지방 이주 서비스의 추천이 무너진다.
 *
 * <p>직종 코드 검증이 <b>캐시 확인보다 먼저</b> 온다. As-Is 순서 그대로다 —
 * 유효하지 않은 코드는 캐시 히트 여부와 무관하게 예외여야 한다.
 *
 * <p>공개 메서드 {@code scoresFor} 에는 {@code @Transactional} 을 붙이지 않는다. 캐시·백분위
 * 조회를 포함해 트랜잭션으로 감싸면 커넥션을 쥔 채 네트워크를 기다리게 된다(persistence-conventions §6.3).
 * DB 조회부({@link #loadCounts})만 잘라 읽기 트랜잭션으로 감싼다.
 */
@Service
@RequiredArgsConstructor
public class JobScoreService {

    private final JobCountRepository jobCountRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final JobScoreCache jobScoreCache;
    private final NonCapitalJobRankingService nonCapitalJobRankingService;

    private final JobScorePolicy policy = new JobScorePolicy();

    /**
     * 전 시군구의 일자리 적합도. {@code jobCode} 가 {@code null} 이면 전체 일자리 수 기준이다.
     * 유효하지 않은 직종 코드면 {@code JOB_CODE_NOT_FOUND} 를 던진다.
     */
    public Map<SigunguCode, Score> scoresFor(JobCode jobCode) {

        if (jobCode != null && !jobCategoryRepository.existsSubCategory(jobCode)) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다.");
        }

        JobScoreKey key = JobScoreKey.of(jobCode);

        // 1) 캐시 확인
        Optional<Map<SigunguCode, Score>> cached = jobScoreCache.find(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) 기준이 되는 일자리 수를 읽어 정책을 적용한다.
        //    DB 조회만 트랜잭션으로 감싸고 캐시·백분위 조회는 트랜잭션 밖에 둔다.
        List<RegionJobCount> counts = loadCounts(key, jobCode);

        // 비수도권 백분위는 job 이 캐시해 둔 최신월 분포에서 나온다. 통계가 적재되지 않았으면
        // 빈 맵이라 일자리 수만 보던 예전 점수와 같아진다.
        Map<SigunguCode, Score> scores =
                policy.scores(counts, nonCapitalJobRankingService.getNonCapitalPercentiles());

        // 3) 캐시 저장. TTL 은 어댑터가 안다.
        jobScoreCache.put(key, scores);

        return scores;
    }

    /** 기준 일자리 수 조회만 트랜잭션으로 감싼다. 캐시·외부호출은 호출부에서 트랜잭션 밖에 둔다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    protected List<RegionJobCount> loadCounts(JobScoreKey key, JobCode jobCode) {
        return key.isAllJobs()
                ? jobCountRepository.findAllRegionTotals()
                : jobCountRepository.findAllRegionCountsOf(jobCode);
    }
}
