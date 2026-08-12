package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.domain.port.JobScoreCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 일자리 수 적재 <b>직전에</b> 파생 점수 캐시를 버린다.
 * As-Is {@code JobCacheCleaner} 를 옮긴 것이다.
 *
 * <p>{@code beforeJob} 이라는 시점도 As-Is 그대로다.
 * (dwelling 은 {@code afterJob} 이었다 — 두 배치의 시점이 원래 다르므로 맞추지 않는다.)
 *
 * <p>As-Is 는 {@code RedisTemplate} 을 직접 들고 {@code keys("job:score:*")} 로 지웠다.
 * 이제 캐시 포트의 {@code evictAll()} 에 위임하므로 이 클래스는 키 문자열도 Redis 도 모른다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobScoreCacheCleaner implements JobExecutionListener {

    private final JobScoreCache jobScoreCache;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        jobScoreCache.evictAll();
    }
}
