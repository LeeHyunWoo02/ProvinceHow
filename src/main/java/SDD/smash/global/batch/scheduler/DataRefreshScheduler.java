package SDD.smash.global.batch.scheduler;

import SDD.smash.global.batch.launch.BatchGuard;
import SDD.smash.global.batch.launch.BatchLaunchGuard;
import SDD.smash.global.batch.launch.BatchLaunchResult;
import SDD.smash.global.batch.seed.SeedStepSpec;

import SDD.smash.domain.dwelling.application.DwellingBaseMonthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정기 갱신 배치의 트리거. seedMasterJob 과 <b>별도로</b> 돌고, 서로의 실행을 침범하지 않는다.
 *
 * <p><b>중복 실행 제어</b>는 전적으로 {@link BatchLaunchGuard} 에 맡긴다 —
 * 진행 중이면 건너뛰고, 같은 기준일/기준월이면 배치 메타가 막는다.
 * seedMasterJob 이 같은 기준으로 이미 Step 을 돌렸다면 {@link BatchGuard#stepAlreadyCompleted}
 * 판정이 그것도 인정한다(Job 이름을 조건에 넣지 않는다).
 *
 * <p><b>주기와 on/off 는 전부 환경변수다.</b> 기본값은 전부 꺼짐이라 값을 주기 전에는 아무것도 돌지 않는다.
 * <table border="1">
 *   <caption>스케줄 프로퍼티</caption>
 *   <tr><th>대상</th><th>on/off</th><th>주기</th><th>기본 주기</th></tr>
 *   <tr><td>인구</td><td>{@code POPULATION_BATCH_ENABLED}</td><td>{@code POPULATION_BATCH_CRON}</td><td>월 1회</td></tr>
 *   <tr><td>지역 인프라(LOCALDATA)</td><td>{@code LOCALDATA_BATCH_ENABLED}</td><td>{@code LOCALDATA_BATCH_CRON}</td><td>일 1회</td></tr>
 *   <tr><td>일자리 수(워크넷 API)</td><td>{@code WORKNET_JOB_BATCH_ENABLED}</td><td>{@code WORKNET_JOB_BATCH_CRON}</td><td>일 1회</td></tr>
 *   <tr><td>전월세</td><td>{@code DWELLING_BATCH_ENABLED}</td><td>{@code DWELLING_BATCH_CRON}</td><td>월 1회</td></tr>
 * </table>
 *
 * <p><b>확장 지점</b> — Job 을 빈 이름이 아니라 {@link Job#getName()} 으로 찾는다.
 * 아직 구현되지 않은 배치(LOCALDATA 수집 등)는 같은 이름의 {@code Job} 빈만
 * 등록하면 이 스케줄러가 그대로 집어간다. Job 이 없으면 경고만 남기고 넘어간다 — 기동을 막지 않는다.
 */
@Component
@Slf4j
public class DataRefreshScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Map<String, Job> jobsByName;
    private final BatchLaunchGuard batchLaunchGuard;

    @Value("${population.batch.enabled:false}") private boolean populationEnabled;
    @Value("${localdata.batch.enabled:false}")  private boolean localdataEnabled;
    @Value("${worknet.job.batch.enabled:false}") private boolean worknetJobEnabled;
    @Value("${dwelling.batch.enabled:false}")   private boolean dwellingEnabled;

    private final DwellingBaseMonthService dwellingBaseMonthService;

    public DataRefreshScheduler(List<Job> jobs, BatchLaunchGuard batchLaunchGuard,
                                DwellingBaseMonthService dwellingBaseMonthService) {
        Map<String, Job> byName = new LinkedHashMap<>();
        jobs.forEach(job -> byName.put(job.getName(), job));
        this.jobsByName = byName;
        this.batchLaunchGuard = batchLaunchGuard;
        this.dwellingBaseMonthService = dwellingBaseMonthService;
    }

    /** 인구 — 월 1회. */
    @Scheduled(cron = "${population.batch.cron:0 10 4 1 * *}")
    public void refreshPopulation() {
        run("PopulationJob", populationEnabled, "POPULATION_BATCH_ENABLED", baseMonthParameters());
    }

    /** 지역 인프라(업종 마스터 + 인프라) — 일 1회. 업종이 인프라의 선행 데이터라 순서를 지킨다. */
    @Scheduled(cron = "${localdata.batch.cron:0 20 3 * * *}")
    public void refreshLocaldata() {
        if (run("industryJob", localdataEnabled, "LOCALDATA_BATCH_ENABLED", baseDateParameters())) {
            run("infraJob", localdataEnabled, "LOCALDATA_BATCH_ENABLED", baseDateParameters());
        }
    }

    /** 일자리 수 — 일 1회. */
    @Scheduled(cron = "${worknet.job.batch.cron:0 0 3 * * *}")
    public void refreshJobCount() {
        run("jobCountJob", worknetJobEnabled, "WORKNET_JOB_BATCH_ENABLED", baseDateParameters());
    }

    /** 전월세 — 월 1회. */
    @Scheduled(cron = "${dwelling.batch.cron:0 0 6 5 * *}")
    public void refreshDwelling() {
        JobParameters parameters = new JobParametersBuilder()
                .addString(SeedStepSpec.BASE_MONTH, baseMonth())
                .addLong("months", (long) dwellingBaseMonthService.lookbackMonths(), false)
                .toJobParameters();
        run("dwellingJob", dwellingEnabled, "DWELLING_BATCH_ENABLED", parameters);
    }

    private boolean run(String jobName, boolean enabled, String enabledKey, JobParameters jobParameters) {
        if (!enabled) {
            log.debug("[batch] job={} 비활성화 ({}=false)", jobName, enabledKey);
            return false;
        }
        Job job = jobsByName.get(jobName);
        if (job == null) {
            log.warn("[batch] job={} 이 아직 등록되지 않았다 - 정기 실행을 건너뛴다.", jobName);
            return false;
        }
        BatchLaunchResult result = batchLaunchGuard.launch(job, jobParameters);
        return result.isLaunched() && !result.execution().getStatus().isUnsuccessful();
    }

    private JobParameters baseDateParameters() {
        return new JobParametersBuilder()
                .addString(SeedStepSpec.BASE_DATE, LocalDate.now(SEOUL).toString())
                .toJobParameters();
    }

    private JobParameters baseMonthParameters() {
        return new JobParametersBuilder()
                .addString(SeedStepSpec.BASE_MONTH, baseMonth())
                .toJobParameters();
    }

    /** 오버라이드 → 확정 지연 적용 → 확정 0건이면 직전 달 fallback. 자세한 규칙은 {@link DwellingBaseMonthService}. */
    private String baseMonth() {
        return dwellingBaseMonthService.resolveBaseMonthText();
    }
}
