package SDD.smash.global.batch.seed;

import SDD.smash.global.batch.launch.BatchGuard;
import SDD.smash.global.batch.launch.BatchLaunchGuard;
import SDD.smash.global.batch.launch.BatchLaunchResult;
import SDD.smash.global.batch.listener.SeedMasterJobListener;

import SDD.smash.domain.dwelling.application.DwellingBaseMonthService;
import SDD.smash.global.config.SeedProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기동 시 {@code seedMasterJob} 을 <b>한 번만</b> 돌린다.
 * 컨텍스트별 Runner 9개({@code @EventListener} + {@code @Order})를 대체하는 유일한 자동 실행 경로다.
 *
 * <h2>JobParameters 정책</h2>
 * <table border="1">
 *   <caption>기준 파라미터</caption>
 *   <tr><th>파라미터</th><th>값</th><th>적용 Step</th></tr>
 *   <tr><td>{@code seedVersion}</td><td>{@code SEED_VERSION}</td>
 *       <td>필수 기준 데이터 — 버전당 1회</td></tr>
 *   <tr><td>{@code baseDate}</td><td>{@code yyyy-MM-dd} 오늘</td>
 *       <td>industry / infra / jobCount — 하루 1회</td></tr>
 *   <tr><td>{@code baseMonth}</td><td>{@code yyyyMM} 이번 달(또는 오버라이드)</td>
 *       <td>population / dwelling — 한 달 1회</td></tr>
 * </table>
 *
 * <p><b>{@code triggerTime} 같은 매번 달라지는 파라미터를 넣지 않는다.</b> 그래야 같은 기준일의 재기동이
 * 같은 JobInstance 로 수렴해 배치 메타의 유니크 제약이 중복 실행을 막는다({@link BatchLaunchGuard}).
 *
 * <p><b>사전 판정 + 예외 방어를 모두 쓴다.</b> {@code JobInstanceAlreadyCompleteException} 을 그대로
 * 맞으면 기동 로그가 스택트레이스로 더럽혀지고 "왜 안 돌았는지"가 드러나지 않는다. 그래서
 * {@link BatchGuard} 로 먼저 판정해 사유를 남기고, 사전 판정과 기동 사이의 경쟁(TOCTOU)은
 * {@link BatchLaunchGuard} 가 예외로 흡수한다. 어느 쪽이든 <b>애플리케이션은 죽지 않는다</b>.
 */
@Component
@Slf4j
public class SeedMasterJobLauncher {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final String UNSPECIFIED_SEED_VERSION = "unspecified";

    private final Job seedMasterJob;
    private final SeedMasterJobListener seedMasterJobListener;
    private final BatchLaunchGuard batchLaunchGuard;
    private final BatchGuard batchGuard;
    private final SeedReadiness seedReadiness;
    private final SeedProperties seedProperties;

    private final DwellingBaseMonthService dwellingBaseMonthService;

    private final boolean enabled;

    public SeedMasterJobLauncher(@Qualifier(SeedMasterJobConfig.SEED_MASTER_JOB) Job seedMasterJob,
                                 SeedMasterJobListener seedMasterJobListener,
                                 BatchLaunchGuard batchLaunchGuard,
                                 BatchGuard batchGuard,
                                 SeedReadiness seedReadiness,
                                 SeedProperties seedProperties,
                                 DwellingBaseMonthService dwellingBaseMonthService,
                                 @Value("${seed.master.enabled:true}") boolean enabled) {
        this.seedMasterJob = seedMasterJob;
        this.seedMasterJobListener = seedMasterJobListener;
        this.batchLaunchGuard = batchLaunchGuard;
        this.batchGuard = batchGuard;
        this.seedReadiness = seedReadiness;
        this.seedProperties = seedProperties;
        this.dwellingBaseMonthService = dwellingBaseMonthService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runSeedMasterJobOnce() {
        if (!enabled) {
            log.info("[seed] seed.master.enabled=false - 기동 시 시드 배치를 실행하지 않는다.");
            return;
        }

        Map<String, String> identifying = identifyingParameters();
        if (batchGuard.jobAlreadyCompleted(SeedMasterJobConfig.SEED_MASTER_JOB, identifying)) {
            log.info("[seed] seedMasterJob 건너뜀 - 같은 기준으로 이미 완료됨 {}", identifying);
            return;
        }

        BatchLaunchResult result = batchLaunchGuard.launch(seedMasterJob, jobParameters(identifying));
        handle(result);
    }

    private void handle(BatchLaunchResult result) {
        switch (result.status()) {
            case LAUNCHED -> {
                JobExecution execution = result.execution();
                List<String> failedEssential = seedMasterJobListener.failedEssentialSteps(execution);
                if (!failedEssential.isEmpty()) {
                    seedReadiness.markNotReady("필수 기준 데이터 적재 실패 steps=" + failedEssential);
                } else if (execution.getStatus() == BatchStatus.FAILED) {
                    seedReadiness.markNotReady("seedMasterJob 실패 exitDescription="
                            + execution.getExitStatus().getExitDescription());
                }
            }
            case FAILED, REJECTED ->
                    seedReadiness.markNotReady("seedMasterJob 기동 실패 reason=" + result.reason());
            case SKIPPED_RUNNING, SKIPPED_ALREADY_COMPLETE ->
                    log.info("[seed] seedMasterJob 건너뜀 reason={}", result.reason());
        }
    }

    /** JobInstance 를 결정하는 파라미터. 여기에 매번 달라지는 값을 넣으면 중복 실행 제어가 무너진다. */
    private Map<String, String> identifyingParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(SeedStepSpec.SEED_VERSION, seedVersion());
        parameters.put(SeedStepSpec.BASE_DATE, LocalDate.now(SEOUL).toString());
        parameters.put(SeedStepSpec.BASE_MONTH, baseMonth());
        return parameters;
    }

    private JobParameters jobParameters(Map<String, String> identifying) {
        JobParametersBuilder builder = new JobParametersBuilder();
        identifying.forEach(builder::addString);
        builder.addLong("months", (long) dwellingBaseMonthService.lookbackMonths(), false);
        return builder.toJobParameters();
    }

    private String seedVersion() {
        String version = seedProperties.getVersion();
        if (version == null || version.isBlank()) {
            log.warn("[seed] SEED_VERSION 이 비어 있다 - 기준 데이터 재실행 방지가 '{}' 로 고정된다.",
                    UNSPECIFIED_SEED_VERSION);
            return UNSPECIFIED_SEED_VERSION;
        }
        return version;
    }

    /**
     * 전월세·인구의 기준월. {@code DWELLING_DEAL_YMD_OVERRIDE} 가 비어 있으면
     * Asia/Seoul 현재월에서 확정 지연({@code dwelling.confirmedLagMonths}) 만큼 물러난 달을 쓰고,
     * 그 달이 확정 0건이면 직전 달로 fallback 한다.
     *
     * <p>fallback 판정은 국토부 API 를 1~4회 탐침한다. 탐침이 실패하거나
     * {@code dwelling.baseMonthProbe.enabled=false} 면 지연만 적용한 값으로 떨어지므로
     * 외부 API 가 죽어 있어도 기동을 막지 않는다.
     *
     * <p>인구도 같은 기준월을 쓴다. 인구 출처(KOSIS)의 확정 시점은 국토부와 다르므로,
     * 인구 API 배치가 붙으면 기준월을 분리할지 재검토해야 한다.
     */
    private String baseMonth() {
        return dwellingBaseMonthService.resolveBaseMonthText();
    }
}
