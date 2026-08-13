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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일자리 적합도 점수 유스케이스. As-Is {@code JobScoreService} 의
 * <b>오케스트레이션 부분만</b> 옮긴 것이다(검증 → 캐시 확인 → 조회 → 정책 적용 → 캐시 저장).
 *
 * <p>점수 공식은 {@code JobScorePolicy}, Redis 상세는 {@code JobScoreRedisAdapter} 로 빠졌다.
 *
 * <p>직종 코드 검증이 <b>캐시 확인보다 먼저</b> 온다. As-Is 순서 그대로다 —
 * 유효하지 않은 코드는 캐시 히트 여부와 무관하게 예외여야 한다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다. 캐시 접근을 포함하는 메서드라
 * 트랜잭션으로 감싸면 커넥션을 쥔 채 네트워크를 기다리게 된다(persistence-conventions §6.3).
 * DB 접근은 조회 한 번뿐이며 As-Is 도 이 경로에 트랜잭션이 없었다.
 */
@Service
@RequiredArgsConstructor
public class JobScoreService {

    private final JobCountRepository jobCountRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final JobScoreCache jobScoreCache;

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
        List<RegionJobCount> counts = key.isAllJobs()
                ? jobCountRepository.findAllRegionTotals()
                : jobCountRepository.findAllRegionCountsOf(jobCode);

        Map<SigunguCode, Score> scores = policy.scores(counts);

        // 3) 캐시 저장. TTL 은 어댑터가 안다.
        jobScoreCache.put(key, scores);

        return scores;
    }
}
