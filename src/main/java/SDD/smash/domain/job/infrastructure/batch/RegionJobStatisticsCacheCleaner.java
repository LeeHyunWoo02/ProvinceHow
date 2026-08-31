package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.domain.port.JobScoreCache;
import SDD.smash.domain.job.domain.port.NonCapitalRatioCache;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 고용행정통계 적재 <b>직후에</b> 파생 캐시를 버린다.
 *
 * <p>{@code JobScoreCacheCleaner} 와 달리 {@code afterJob} 이다 — 파생값이 이 배치가 넣는
 * 행에서 나오므로 적재가 끝난 뒤에 지워야 다음 요청이 새 데이터로 다시 만든다.
 *
 * <p>버리는 대상은 둘이다. 비수도권 구인배수 분포는 이 통계에서 바로 나오고, 일자리 점수는
 * 그 분포의 백분위를 섞어 계산하므로 통계가 바뀌면 함께 낡는다.
 */
@Component
@RequiredArgsConstructor
public class RegionJobStatisticsCacheCleaner implements JobExecutionListener {

    private final NonCapitalRatioCache nonCapitalRatioCache;
    private final JobScoreCache jobScoreCache;

    @Override
    public void afterJob(JobExecution jobExecution) {
        nonCapitalRatioCache.evictAll();
        jobScoreCache.evictAll();
    }
}
