package SDD.smash.support.infrastructure.scheduler;

import SDD.smash.support.application.port.in.RefreshSupportPolicyUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지원정책 원본 주기 갱신 트리거. As-Is {@code YouthSupportScheduler} 를 대체할 자리다.
 *
 * <p><b>지금은 {@code @Scheduled} 를 붙이지 않는다.</b> 옛 {@code YouthSupportScheduler}
 * 가 이미 3일 주기로 실제 청년정책 API 를 전 시군구 × 전 태그에 대해 호출하고 있다.
 * 이 클래스에도 {@code @Scheduled} 를 달면 같은 일을 하는 스케줄러가 두 개 떠서
 * <b>외부 API 호출이 두 배로 나가는 실제 운영 영향</b>이 생긴다 — "동작 무변경" 원칙 위반이다.
 *
 * <p>{@code recommendation} 단계에서 옛 스케줄러를 걷어내고 {@code Apis}/{@code Support}
 * 호출부를 이 컨텍스트의 in-port로 옮길 때, 아래 주석을 해제해 활성화한다.
 *
 * <pre>{@code
 * @Scheduled(initialDelay = 0, fixedDelayString = "#{T(java.time.Duration).ofDays(3).toMillis()}")
 * public void refresh() {
 *     refreshSupportPolicyUseCase.refreshAll();
 * }
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class SupportPolicyRefreshScheduler {

    private final RefreshSupportPolicyUseCase refreshSupportPolicyUseCase;

    /** 활성화 방법은 클래스 주석을 참고한다. 지금은 아무도 호출하지 않는다. */
    public void refresh() {
        refreshSupportPolicyUseCase.refreshAll();
    }
}
