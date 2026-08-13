package SDD.smash.global.batch.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 필수 기준 데이터 적재 결과를 애플리케이션의 <b>준비 상태</b>로 표현한다.
 *
 * <p><b>왜 컨텍스트를 종료하지 않는가</b> — docker-compose 에 {@code restart: unless-stopped} 가 걸려 있다.
 * 기동 실패로 프로세스를 죽이면 CSV 가 고쳐지기 전까지 컨테이너가 무한 재시작 루프에 빠지고,
 * 그 사이 로그가 재시작 배너로 덮여 원인을 찾기 더 어려워진다.
 * 그래서 <b>프로세스는 살리고 준비 완료 표시만 내린다</b>.
 *
 * <p>표현 수단은 Spring Boot 코어의 {@link ReadinessState#REFUSING_TRAFFIC} 이다.
 * {@code spring-boot-starter-actuator} 없이 쓸 수 있는(= 새 의존성을 추가하지 않는) 표준 신호이며,
 * 나중에 actuator 를 넣으면 {@code /actuator/health/readiness} 에 그대로 노출된다.
 * 그때까지는 ERROR 로그와 이 빈의 상태, 그리고 배치 메타의 FAILED 이력이 판정 근거다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedReadiness {

    private final ApplicationEventPublisher eventPublisher;

    private volatile String notReadyReason;

    /** 필수 기준 데이터가 준비되지 않았음을 알린다. */
    public void markNotReady(String reason) {
        this.notReadyReason = reason;
        log.error("[seed] 준비 완료 아님 - 필수 기준 데이터가 적재되지 않았다. reason={}", reason);
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
    }

    public boolean isReady() {
        return notReadyReason == null;
    }

    public Optional<String> notReadyReason() {
        return Optional.ofNullable(notReadyReason);
    }
}
