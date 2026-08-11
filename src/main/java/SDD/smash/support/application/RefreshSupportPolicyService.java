package SDD.smash.support.application;

import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.support.application.port.in.RefreshSupportPolicyUseCase;
import SDD.smash.support.domain.model.SupportTag;
import SDD.smash.support.domain.port.SupportPolicyProvider;
import SDD.smash.support.domain.port.SupportPolicyRepository;
import SDD.smash.support.domain.port.SupportScoreCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지원정책 원본 갱신 유스케이스. As-Is {@code YouthSupportScheduler.runJob()} 의
 * 갱신 로직을 옮긴 것이다 — 전 시군구 × 전 태그를 순회해 외부 API 를 호출하고,
 * 끝나면 파생 점수 캐시를 무효화한다(redis-conventions §6.1).
 *
 * <p>{@code recommendation} 단계에서 {@code support/infrastructure/scheduler/SupportPolicyRefreshScheduler}
 * 가 3일 주기로 이 유스케이스를 트리거하도록 활성화됐고, 같은 커밋에서 옛
 * {@code YouthSupportScheduler} 를 삭제했다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSupportPolicyService implements RefreshSupportPolicyUseCase {

    private final AddressQueryUseCase addressQueryUseCase;
    private final SupportPolicyProvider supportPolicyProvider;
    private final SupportPolicyRepository supportPolicyRepository;
    private final SupportScoreCache supportScoreCache;

    @Override
    public void refreshAll() {
        long started = System.nanoTime();
        int saved = 0;
        for (SigunguCode code : addressQueryUseCase.getAllSigunguCodes()) {
            for (SupportTag tag : SupportTag.values()) {
                try {
                    supportPolicyRepository.saveAll(code, tag, supportPolicyProvider.fetch(code, tag));
                    saved++;
                } catch (RuntimeException e) {
                    log.warn("[SupportRefresh] 실패 sigungu={}, tag={}", code.value(), tag, e);
                }
            }
        }
        supportScoreCache.evictAll();
        log.info("[SupportRefresh] 완료 saved={}, elapsed={}ms", saved, elapsedMs(started));
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
