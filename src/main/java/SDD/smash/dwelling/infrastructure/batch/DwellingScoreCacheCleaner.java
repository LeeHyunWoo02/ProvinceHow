package SDD.smash.dwelling.infrastructure.batch;

import SDD.smash.dwelling.domain.port.DwellingScoreCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 시세 적재가 끝나면 파생 점수 캐시를 버린다.
 * As-Is {@code DwellingCacheCleaner} 를 옮긴 것이다.
 *
 * <p>As-Is 는 {@code RedisTemplate} 을 직접 들고 {@code keys("dwelling:score:*")} 로 지웠다.
 * 이제 캐시 포트의 {@code evictAll()} 에 위임하므로 이 클래스는 키 문자열도 Redis 도 모른다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DwellingScoreCacheCleaner implements JobExecutionListener {

    private final DwellingScoreCache dwellingScoreCache;

    @Override
    public void afterJob(JobExecution jobExecution) {
        dwellingScoreCache.evictAll();
    }
}
