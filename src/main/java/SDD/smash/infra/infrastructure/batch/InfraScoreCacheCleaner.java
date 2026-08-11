package SDD.smash.infra.infrastructure.batch;

import SDD.smash.infra.domain.port.InfraScoreCache;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 인프라 적재 후 파생 점수 캐시를 버리기 <b>위해 존재했던</b> 리스너.
 * As-Is {@code InfraCacheCleaner} 를 옮긴 것이다.
 *
 * <p><b>주의 — As-Is 그대로 옮겼을 뿐 어느 Job 에도 연결돼 있지 않다.</b>
 * As-Is {@code InfraBatch}/{@code IndustryBatch} 는 {@code JobBuilder...listener(...)} 를
 * 호출하지 않아 이 리스너가 실제로는 한 번도 실행되지 않는 고아 컴포넌트였다
 * (dwelling/job 의 대응 클래스는 각자의 Job 에 연결돼 있었다 — 여기만 빠져 있다).
 * 이 이관은 "동작을 바꾸지 않는다"는 원칙에 따라 <b>연결하지 않은 채로 옮긴다</b>.
 * 연결하면 지금까지 없던 자동 무효화가 새로 생기는 것이므로 그 자체가 동작 변경이다.
 * 발견한 문제로 보고하며, 고치는 것은 별도 작업이다.
 */
@Component
@RequiredArgsConstructor
public class InfraScoreCacheCleaner implements JobExecutionListener {

    private final InfraScoreCache infraScoreCache;

    @Override
    public void afterJob(JobExecution jobExecution) {
        infraScoreCache.evictAll();
    }
}
