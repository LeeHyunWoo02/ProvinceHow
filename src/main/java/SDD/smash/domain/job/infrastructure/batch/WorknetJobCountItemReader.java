package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.domain.model.JobCountKey;
import SDD.smash.domain.job.domain.model.JobPosting;
import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.domain.port.JobPostingProvider;
import SDD.smash.domain.job.infrastructure.batch.dto.JobCountCsvRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCountJpaRepository;
import SDD.smash.domain.job.infrastructure.persistence.projection.JobCountKeyRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 워크넷 채용정보 API 로 {@code JobCount} 를 채우는 Reader.
 *
 * <p><b>CSV 중간산출물을 만들지 않는다.</b> API → (집계) → Processor → DB Upsert 로 곧장 간다.
 * 기존 {@code jobCountCsvProcessor} 와 {@code jobCountWriter}(Upsert SQL)를 <b>그대로</b> 재사용하려고
 * 출력 타입을 {@link JobCountCsvRow} 로 맞췄다 — 이름만 CSV 일 뿐 (시군구, 직종, 건수) 튜플이다.
 *
 * <h2>메모리</h2>
 * 공고 원문은 <b>페이지 단위로만</b> 들고 있다가 즉시 집계에 접어 넣고 버린다.
 * 상주하는 것은 두 가지뿐이다.
 * <ul>
 *   <li>집계 맵 — (시군구 264) × (직종중분류 114) 가 상한이라 3만 항목을 넘지 않는다</li>
 *   <li>중복 제거용 공고 ID 집합 — {@code maxPages × pageSize} 로 상한이 걸린다(기본 10만)</li>
 * </ul>
 *
 * <h2>스냅샷 교체</h2>
 * 이번 스냅샷에 없는 (시군구, 직종) 조합은 <b>행을 지우지 않고 {@code count = 0} 으로 내린다.</b>
 * 근거는 docs/worknet-job-api.md 참조. 덕분에 Writer 의 {@code ON DUPLICATE KEY UPDATE count = VALUES(count)}
 * 를 그대로 두고도 "지난 회차에만 있던 값이 남는" 문제가 생기지 않는다.
 *
 * <h2>재시작</h2>
 * 중간 재시작을 지원하지 않는다. Step 이 다시 열리면 API 부터 다시 수집한다 —
 * 부분 집계를 이어 붙이면 건수가 틀리기 때문이다. 같은 기준일 재실행은
 * {@code count = VALUES(count)} 라 몇 번을 돌려도 같은 상태로 수렴한다(멱등).
 */
@Component
@Slf4j
public class WorknetJobCountItemReader implements ItemStreamReader<JobCountCsvRow>, StepExecutionListener {

    private static final String BATCH_NAME = "jobCountStep";
    private static final String BASE_DATE_PARAMETER = "baseDate";

    private final JobPostingProvider jobPostingProvider;
    private final JobCountJpaRepository jobCountJpaRepository;

    private final int pageSize;
    private final int maxPages;
    private final boolean dedupeEnabled;
    private final boolean resetMissingToZero;

    private String baseDate = "-";
    private Iterator<JobCountCsvRow> cursor;
    private long startedAtMillis;

    private int apiCallCount;
    private int readPostingCount;
    private int duplicateCount;
    private int unresolvedRegionCount;
    private int unresolvedJobCount;
    private int emittedRowCount;
    private int zeroedRowCount;

    public WorknetJobCountItemReader(
            JobPostingProvider jobPostingProvider,
            JobCountJpaRepository jobCountJpaRepository,
            @Value("${worknet.job.page-size:100}") int pageSize,
            @Value("${worknet.job.max-pages:1000}") int maxPages,
            @Value("${worknet.job.dedupe-enabled:true}") boolean dedupeEnabled,
            @Value("${worknet.job.reset-missing-to-zero:true}") boolean resetMissingToZero) {
        this.jobPostingProvider = jobPostingProvider;
        this.jobCountJpaRepository = jobCountJpaRepository;
        this.pageSize = Math.max(1, pageSize);
        this.maxPages = Math.max(1, maxPages);
        this.dedupeEnabled = dedupeEnabled;
        this.resetMissingToZero = resetMissingToZero;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String parameter = stepExecution.getJobParameters().getString(BASE_DATE_PARAMETER);
        baseDate = (parameter == null || parameter.isBlank()) ? "-" : parameter;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long elapsed = System.currentTimeMillis() - startedAtMillis;
        Throwable failure = stepExecution.getFailureExceptions().stream().findFirst().orElse(null);
        log.info("[{}] baseDate={} status={} apiCalls={} readPostings={} duplicates={} "
                        + "unresolvedRegion={} unresolvedJob={} emittedRows={} zeroedRows={} "
                        + "written={} elapsed={}ms",
                BATCH_NAME, baseDate, stepExecution.getStatus(), apiCallCount, readPostingCount,
                duplicateCount, unresolvedRegionCount, unresolvedJobCount, emittedRowCount,
                zeroedRowCount, stepExecution.getWriteCount(), elapsed);
        if (failure != null) {
            log.error("[{}] baseDate={} 실패 원인", BATCH_NAME, baseDate, failure);
        }
        return stepExecution.getExitStatus();
    }

    @Override
    public void open(ExecutionContext executionContext) {
        cursor = null;
        startedAtMillis = System.currentTimeMillis();
        apiCallCount = 0;
        readPostingCount = 0;
        duplicateCount = 0;
        unresolvedRegionCount = 0;
        unresolvedJobCount = 0;
        emittedRowCount = 0;
        zeroedRowCount = 0;
    }

    @Override
    public void update(ExecutionContext executionContext) {
        // 중간 상태를 저장하지 않는다 - 부분 집계로 재시작하면 건수가 틀린다.
    }

    @Override
    public void close() {
        cursor = null;
    }

    @Override
    public JobCountCsvRow read() {
        if (cursor == null) {
            cursor = collect().iterator();
        }
        return cursor.hasNext() ? cursor.next() : null;
    }

    /** API 를 페이지 단위로 끝까지 당겨 집계한 뒤, Upsert 할 행 목록으로 편다. */
    private List<JobCountCsvRow> collect() {
        if (!jobPostingProvider.isConfigured()) {
            // 빈 키로 API 를 부르지 않는다. 실패시키지도 않는다 -
            // 사용자가 활용신청·승인을 마치기 전에도 애플리케이션은 정상 기동해야 한다.
            // 아무 행도 내보내지 않으므로 0 리셋도 일어나지 않는다(기존 데이터를 지우지 않는다).
            log.warn("[{}] baseDate={} 채용정보 API 인증키(apis.datagokr.service-key / DATA_GO_KR_SERVICE_KEY)가 "
                            + "비어 있어 일자리 수를 적재하지 않는다. 이 데이터는 '미적재' 상태로 남는다. "
                            + "키를 넣거나 worknet.job.batch.enabled=false 로 레거시 CSV 경로를 쓰라.",
                    BATCH_NAME, baseDate);
            return List.of();
        }

        int effectivePageSize = Math.min(pageSize, jobPostingProvider.maxPageSize());
        Map<JobCountKey, Integer> aggregate = new LinkedHashMap<>();
        Set<JobPostingId> seen = dedupeEnabled ? new HashSet<>() : null;

        log.info("[{}] baseDate={} 수집 시작 pageSize={}, maxPages={}, dedupe={}",
                BATCH_NAME, baseDate, effectivePageSize, maxPages, dedupeEnabled);

        for (int pageNumber = 1; pageNumber <= maxPages; pageNumber++) {
            JobPostingPage page = jobPostingProvider.fetchPage(pageNumber, effectivePageSize);
            apiCallCount++;

            unresolvedRegionCount += page.unresolvedRegionCount();
            unresolvedJobCount += page.unresolvedJobCount();
            accumulate(page, aggregate, seen);

            if (page.isEmpty()) {
                break;
            }
            if (!page.hasNext(effectivePageSize)) {
                break;
            }
            if (pageNumber == maxPages) {
                log.warn("[{}] 페이지 상한({})에 도달해 수집을 끊는다. 상한을 올리거나 조건을 좁혀라.",
                        BATCH_NAME, maxPages);
            }
        }

        List<JobCountCsvRow> rows = toRows(aggregate);
        log.info("[{}] baseDate={} 수집 완료 apiCalls={}, readPostings={}, duplicates={}, "
                        + "unresolvedRegion={}, unresolvedJob={}, rows={}, zeroedRows={}",
                BATCH_NAME, baseDate, apiCallCount, readPostingCount, duplicateCount,
                unresolvedRegionCount, unresolvedJobCount, rows.size(), zeroedRowCount);
        return rows;
    }

    private void accumulate(JobPostingPage page, Map<JobCountKey, Integer> aggregate, Set<JobPostingId> seen) {
        for (JobPosting posting : page.postings()) {
            if (seen != null && !seen.add(posting.id())) {
                duplicateCount++;
                continue;
            }
            if (!posting.isCountable()) {
                continue;
            }
            readPostingCount++;
            for (JobCountKey key : posting.countKeys()) {
                aggregate.merge(key, 1, Integer::sum);
            }
        }
    }

    /** 집계 결과 + (스냅샷에서 사라진 조합의) 0 행. */
    private List<JobCountCsvRow> toRows(Map<JobCountKey, Integer> aggregate) {
        List<JobCountCsvRow> rows = new ArrayList<>(aggregate.size());
        Set<String> collectedKeys = new HashSet<>();

        aggregate.forEach((key, count) -> {
            String sigunguCode = key.sigunguCode().value();
            String jobCode = key.jobCode().value();
            rows.add(new JobCountCsvRow(sigunguCode, jobCode, count));
            collectedKeys.add(sigunguCode + "|" + jobCode);
        });
        emittedRowCount = rows.size();

        if (!resetMissingToZero) {
            return rows;
        }
        for (JobCountKeyRow existing : jobCountJpaRepository.findAllKeys()) {
            String composite = existing.sigunguCode() + "|" + existing.jobCodeMiddleCode();
            if (collectedKeys.add(composite)) {
                rows.add(new JobCountCsvRow(existing.sigunguCode(), existing.jobCodeMiddleCode(), 0));
                zeroedRowCount++;
            }
        }
        return rows;
    }
}
