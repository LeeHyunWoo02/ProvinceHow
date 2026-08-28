package SDD.smash.global.metrics;

import SDD.smash.global.batch.launch.BatchGuard;
import SDD.smash.global.batch.launch.RunningJobExecution;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 배치 실행 상태를 노출하는 계측기.
 *
 * <p>재배포가 죽인 실행이 {@code END_TIME IS NULL} 로 남으면 이후 기동이 전부 건너뛰기가 되는데,
 * 증상이 WARN 한 줄뿐이라 며칠을 모른 채 지나갔다. "몇 시간째 STARTED 인가" 와 "기동을 몇 번
 * 건너뛰었나" 를 값으로 내보내는 것이 이 계측기의 목적이다.
 *
 * <p>메트릭
 * <ul>
 *   <li>{@code smash_batch_running_executions{job}} — 진행 중인 실행 수</li>
 *   <li>{@code smash_batch_oldest_running_age_seconds{job}} — 가장 오래된 진행 중 실행의 나이(시작 기준)</li>
 *   <li>{@code smash_batch_stale_recovered_total{job}} — 고아 실행 정리 횟수</li>
 *   <li>{@code smash_batch_launch_skipped_total{job,reason}} — 기동을 건너뛰거나 실패한 횟수</li>
 * </ul>
 *
 * <p>게이지는 {@link CallBudgetMetrics} 와 같이 <b>스크랩 시점에 공급자를 호출</b>한다. 다만 공급자가
 * 메타 DB 를 읽으므로 스크랩(30초)마다 쿼리가 나가지 않도록 스냅샷을 {@value #CACHE_SECONDS}초 캐시한다.
 *
 * <p>시각은 배치 메타에 저장된 값과 같은 기준(JVM 로컬 {@code LocalDateTime})으로 비교한다.
 */
@Component
public class BatchExecutionMetrics {

    private static final String RUNNING = "smash.batch.running.executions";
    private static final String OLDEST_AGE = "smash.batch.oldest.running.age.seconds";
    private static final String STALE_RECOVERED = "smash.batch.stale.recovered";
    private static final String LAUNCH_SKIPPED = "smash.batch.launch.skipped";

    static final int CACHE_SECONDS = 30;
    private static final Duration CACHE_TTL = Duration.ofSeconds(CACHE_SECONDS);

    private final MeterRegistry registry;
    private final BatchGuard batchGuard;
    private final Clock clock;

    private volatile Snapshot snapshot;

    @Autowired
    public BatchExecutionMetrics(MeterRegistry registry, BatchGuard batchGuard, List<Job> jobs) {
        this(registry, batchGuard, Clock.systemDefaultZone(), jobs.stream().map(Job::getName).toList());
    }

    /** 테스트에서 고정 {@link Clock} 을 넣기 위한 생성자. */
    public BatchExecutionMetrics(MeterRegistry registry, BatchGuard batchGuard, Clock clock,
                                 Collection<String> jobNames) {
        this.registry = registry;
        this.batchGuard = batchGuard;
        this.clock = clock;
        jobNames.forEach(this::register);
    }

    /** 한 Job 의 진행 상태 게이지를 등록한다. 실행 이력이 없으면 0 이 나온다. */
    public void register(String jobName) {
        Gauge.builder(RUNNING, () -> runningOf(jobName).count())
                .description("진행 중(END_TIME 없음)인 배치 실행 수")
                .tag("job", jobName)
                .strongReference(true)
                .register(registry);

        Gauge.builder(OLDEST_AGE, () -> runningOf(jobName).oldestAgeSeconds())
                .description("가장 오래된 진행 중 배치 실행의 나이(초)")
                .tag("job", jobName)
                .strongReference(true)
                .register(registry);
    }

    /** 고아 실행을 하나 정리했다. */
    public void staleRecovered(String jobName) {
        Counter.builder(STALE_RECOVERED)
                .description("고아(stale) 배치 실행을 FAILED 로 정리한 횟수")
                .tag("job", jobName)
                .register(registry)
                .increment();
    }

    /**
     * 기동을 건너뛰거나 실패했다.
     *
     * @param reason 낮은 카디널리티의 고정 사유 코드. 자유 문자열을 넣지 않는다
     */
    public void launchSkipped(String jobName, String reason) {
        Counter.builder(LAUNCH_SKIPPED)
                .description("배치 기동을 건너뛰거나 실패한 횟수")
                .tag("job", jobName)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    private Running runningOf(String jobName) {
        return snapshotNow().byJob().getOrDefault(jobName, Running.NONE);
    }

    private Snapshot snapshotNow() {
        LocalDateTime now = LocalDateTime.now(clock);
        Snapshot cached = snapshot;
        if (cached != null && Duration.between(cached.takenAt(), now).compareTo(CACHE_TTL) < 0) {
            return cached;
        }

        Map<String, Running> byJob = new LinkedHashMap<>();
        for (RunningJobExecution running : batchGuard.findRunningExecutions()) {
            long ageSeconds = Math.max(0L, running.runningFor(now).toSeconds());
            byJob.merge(running.jobName(), new Running(1, ageSeconds),
                    (a, b) -> new Running(a.count() + b.count(),
                            Math.max(a.oldestAgeSeconds(), b.oldestAgeSeconds())));
        }

        Snapshot refreshed = new Snapshot(now, byJob);
        snapshot = refreshed;
        return refreshed;
    }

    private record Running(int count, long oldestAgeSeconds) {
        static final Running NONE = new Running(0, 0L);
    }

    private record Snapshot(LocalDateTime takenAt, Map<String, Running> byJob) {}
}
