package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.port.InfraScoreCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 인프라 적재 후 파생 점수 캐시를 버리는 리스너.
 *
 * <p><b>이제 {@code infraJob} 에 연결돼 있다.</b> As-Is 는 {@code JobBuilder...listener(...)} 를
 * 호출하지 않아 한 번도 실행되지 않는 고아 컴포넌트였고(dwelling/job 의 대응 클래스는 연결돼 있었다),
 * 그 결과 인프라를 갱신해도 {@code infra:score:*} 캐시 TTL(24시간)이 만료될 때까지
 * 옛 점수가 계속 나갔다. redis-conventions §6.1 — 원본을 갱신하는 쪽이 파생 캐시 무효화를 책임진다.
 *
 * <p>캐시 정리 실패가 배치를 죽이면 안 되므로 예외를 삼키고 경고만 남긴다.
 * TTL 이 결국 정리한다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InfraScoreCacheCleaner implements JobExecutionListener {

    private final InfraScoreCache infraScoreCache;

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            infraScoreCache.evictAll();
            log.info("[infraJob] 인프라 점수 캐시 무효화 완료 jobStatus={}", jobExecution.getStatus());
        } catch (RuntimeException e) {
            log.warn("[infraJob] 인프라 점수 캐시 무효화 실패 - TTL 만료를 기다린다.", e);
        }
    }
}
