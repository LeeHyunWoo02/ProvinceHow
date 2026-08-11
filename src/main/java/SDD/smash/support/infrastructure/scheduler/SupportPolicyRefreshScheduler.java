package SDD.smash.support.infrastructure.scheduler;

import SDD.smash.support.application.port.in.RefreshSupportPolicyUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지원정책 원본 주기 갱신 트리거. As-Is {@code YouthSupportScheduler} 를 대체한다.
 *
 * <p><b>recommendation 단계에서 활성화됐다.</b> {@code Apis/Service/*} 가 이 컨텍스트의
 * {@code SupportQueryUseCase}/{@code SupportScoreUseCase} 로 전환되고, 옛
 * {@code YouthSupportScheduler} 가 삭제된 것과 같은 커밋에서 이 {@code @Scheduled} 를
 * 켰다 — 두 스케줄러가 동시에 떠서 외부 API 호출이 이중으로 나가는 일이 없도록,
 * 옛 스케줄러 제거와 이 활성화는 항상 한 세트로 다닌다.
 */
@Component
@RequiredArgsConstructor
public class SupportPolicyRefreshScheduler {

    private final RefreshSupportPolicyUseCase refreshSupportPolicyUseCase;

    @Scheduled(initialDelay = 0, fixedDelayString = "#{T(java.time.Duration).ofDays(3).toMillis()}")
    public void refresh() {
        refreshSupportPolicyUseCase.refreshAll();
    }
}
