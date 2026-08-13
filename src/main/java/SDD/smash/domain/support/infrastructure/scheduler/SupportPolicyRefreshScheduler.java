package SDD.smash.domain.support.infrastructure.scheduler;

import SDD.smash.domain.support.application.RefreshSupportPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지원정책 원본 주기 갱신 트리거.
 *
 * <p>인바운드 어댑터이므로 <b>application 유스케이스에 위임만</b> 한다 —
 * Redis 나 외부 API 를 직접 다루지 않는다(architecture-conventions §6.3).
 * 파생 캐시 무효화 책임도 유스케이스에 있다.
 */
@Component
@RequiredArgsConstructor
public class SupportPolicyRefreshScheduler {

    private final RefreshSupportPolicyService refreshSupportPolicyService;

    @Scheduled(initialDelay = 0, fixedDelayString = "#{T(java.time.Duration).ofDays(3).toMillis()}")
    public void refresh() {
        refreshSupportPolicyService.refreshAll();
    }
}
