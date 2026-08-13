package SDD.smash.domain.support.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyProvider;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import SDD.smash.domain.support.domain.port.SupportScoreCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지원정책 원본 갱신 유스케이스 — 전 시군구 × 전 태그를 순회해 외부 API 를 호출하고,
 * 끝나면 파생 점수 캐시를 무효화한다(redis-conventions §6.1).
 *
 * <p>{@code support/infrastructure/scheduler/SupportPolicyRefreshScheduler} 가
 * 3일 주기로 이 유스케이스를 트리거한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSupportPolicyService {

    private final AddressQueryService addressQueryService;
    private final SupportPolicyProvider supportPolicyProvider;
    private final SupportPolicyRepository supportPolicyRepository;
    private final SupportScoreCache supportScoreCache;

    /** 항목 단위로 실패를 흡수하고 계속 진행한다. 끝나면 파생 점수 캐시를 전부 버린다. */
    public void refreshAll() {
        long started = System.nanoTime();
        int saved = 0;
        for (SigunguCode code : addressQueryService.getAllSigunguCodes()) {
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
