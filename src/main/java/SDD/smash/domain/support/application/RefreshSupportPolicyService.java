package SDD.smash.domain.support.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportPolicyCollection;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyProvider;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import SDD.smash.domain.support.domain.port.SupportScoreCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * 지원정책 원본 갱신 유스케이스 — 전 시군구 × 전 태그를 순회해 외부 API 를 호출하고,
 * 끝나면 파생 점수 캐시를 무효화한다(redis-conventions §6.1).
 *
 * <p>{@code support/infrastructure/scheduler/SupportPolicyRefreshScheduler} 가
 * 3일 주기로 이 유스케이스를 트리거한다.
 *
 * <h2>수집 실패는 저장하지 않는다</h2>
 * 정본이 Redis 라 빈 목록을 저장하면 그 키가 즉시 비어버린다 — 일시적인 500 하나가
 * 그 (시군구, 태그) 의 정책을 지우는 것이 실제로 일어났다. 그래서
 * {@link SupportPolicyCollection#collected()} 가 거짓이면 저장을 건너뛰고 기존 값을 살려 둔다
 * (LOCALDATA 부분 반영 금지, JobCount 예산 초과 시 run 폐기와 같은 결).
 *
 * <h2>두 가지 종료 조건 — 먼저 걸리는 쪽으로 끝낸다</h2>
 * <ul>
 *   <li><b>연속 실패 임계</b> — 연속으로 N 개 조합이 실패하면 외부 서버가 죽은 것으로 보고 중단한다.
 *       한 번이라도 성공하면 카운터는 0 으로 돌아가므로 산발적 실패로는 걸리지 않는다.</li>
 *   <li><b>데드라인</b> — 전체 실행 시간이 상한을 넘으면 남은 조합을 건너뛰고 끝낸다.</li>
 * </ul>
 * 중단은 <b>실패가 아니라 정상 종료</b>다. 이미 성공한 조합의 저장은 그대로 남고 남은 조합은
 * 다음 실행이 이어받는다(LOCALDATA 의 일일 호출 예산 소진을 정상 종료로 다룬 것과 같은 결).
 */
@Service
@Slf4j
public class RefreshSupportPolicyService {

    private final AddressQueryService addressQueryService;
    private final SupportPolicyProvider supportPolicyProvider;
    private final SupportPolicyRepository supportPolicyRepository;
    private final SupportScoreCache supportScoreCache;

    /** 0 이하면 연속 실패로 중단하지 않는다. */
    private final int maxConsecutiveFailures;
    /** 0 이하면 데드라인이 없다. */
    private final long deadlineMillis;
    private final Clock clock;

    @Autowired
    public RefreshSupportPolicyService(
            AddressQueryService addressQueryService,
            SupportPolicyProvider supportPolicyProvider,
            SupportPolicyRepository supportPolicyRepository,
            SupportScoreCache supportScoreCache,
            @Value("${support.refresh.max-consecutive-failures:20}") int maxConsecutiveFailures,
            @Value("${support.refresh.deadline-minutes:45}") long deadlineMinutes) {

        this(addressQueryService, supportPolicyProvider, supportPolicyRepository, supportScoreCache,
                maxConsecutiveFailures, deadlineMinutes, Clock.systemUTC());
    }

    /** 시계를 고정해 종료 조건을 검증하기 위한 생성자. */
    RefreshSupportPolicyService(
            AddressQueryService addressQueryService,
            SupportPolicyProvider supportPolicyProvider,
            SupportPolicyRepository supportPolicyRepository,
            SupportScoreCache supportScoreCache,
            int maxConsecutiveFailures,
            long deadlineMinutes,
            Clock clock) {

        this.addressQueryService = addressQueryService;
        this.supportPolicyProvider = supportPolicyProvider;
        this.supportPolicyRepository = supportPolicyRepository;
        this.supportScoreCache = supportScoreCache;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.deadlineMillis = deadlineMinutes <= 0 ? 0 : deadlineMinutes * 60_000L;
        this.clock = clock;
    }

    /**
     * 조합(시군구 × 태그) 단위로 실패를 흡수하고 계속 진행한다. 끝나면 파생 점수 캐시를 전부 버린다.
     *
     * <p>집계는 <b>조합 수가 아니라 실제 적재된 정책 수</b>({@code saved})를 센다. 예전 로그의
     * {@code saved=1056} 은 264×4 반복 횟수였고, 실패한 조합까지 포함해 아무것도 알려주지 않았다.
     * 중단됐다면 사유({@code stopped})와 남은 조합 수({@code remaining})를 함께 남겨
     * <b>로그만 보고 왜 전부 돌지 않았는지</b> 알 수 있게 한다.
     */
    public void refreshAll() {
        long startedAt = clock.millis();
        List<SigunguCode> codes = addressQueryService.getAllSigunguCodes();
        int total = codes.size() * SupportTag.values().length;

        int combinations = 0;
        int saved = 0;
        int succeeded = 0;
        int skipped = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        StopReason stopReason = StopReason.NONE;

        outer:
        for (SigunguCode code : codes) {
            for (SupportTag tag : SupportTag.values()) {
                if (deadlineExceeded(startedAt)) {
                    stopReason = StopReason.DEADLINE;
                    break outer;
                }
                combinations++;
                try {
                    SupportPolicyCollection collection = supportPolicyProvider.fetch(code, tag);
                    if (!collection.collected()) {
                        // 수집 실패. 저장하면 기존 정책이 빈 값으로 덮인다. 그대로 둔다.
                        skipped++;
                        consecutiveFailures++;
                    } else {
                        supportPolicyRepository.saveAll(code, tag, collection.policies());
                        succeeded++;
                        saved += collection.size();
                        consecutiveFailures = 0;
                    }
                } catch (RuntimeException e) {
                    // 저장소 장애 등. 사유는 여기서 남기고 다음 조합을 계속한다.
                    failed++;
                    consecutiveFailures++;
                    log.warn("[SupportRefresh] 실패 sigungu={}, tag={}", code.value(), tag, e);
                }

                if (consecutiveFailuresReached(consecutiveFailures)) {
                    stopReason = StopReason.CONSECUTIVE_FAILURES;
                    break outer;
                }
            }
        }

        if (stopReason != StopReason.NONE) {
            log.warn("[SupportRefresh] 중단 reason={}, {}, 처리={}/{}, 남은 조합={}"
                            + " — 실패가 아니라 정상 종료다. 저장된 조합은 유지되고 남은 조합은 다음 실행이 이어받는다.",
                    stopReason, stopReason.description(), combinations, total, total - combinations);
        }

        supportScoreCache.evictAll();
        log.info("[SupportRefresh] 완료 saved={}, combinations={}, succeeded={}, skipped={}, failed={},"
                        + " stopped={}, remaining={}, elapsed={}ms",
                saved, combinations, succeeded, skipped, failed,
                stopReason, total - combinations, clock.millis() - startedAt);
    }

    private boolean deadlineExceeded(long startedAt) {
        return deadlineMillis > 0 && clock.millis() - startedAt >= deadlineMillis;
    }

    private boolean consecutiveFailuresReached(int consecutiveFailures) {
        return maxConsecutiveFailures > 0 && consecutiveFailures >= maxConsecutiveFailures;
    }

    /** 이번 실행이 왜 끝났는가. 집계 로그에 그대로 실린다. */
    private enum StopReason {
        NONE("끝까지 순회했다"),
        CONSECUTIVE_FAILURES("연속 실패가 임계에 도달했다(외부 서버 장애로 판단)"),
        DEADLINE("실행 시간이 상한을 넘었다");

        private final String description;

        StopReason(String description) {
            this.description = description;
        }

        String description() {
            return description;
        }
    }
}
