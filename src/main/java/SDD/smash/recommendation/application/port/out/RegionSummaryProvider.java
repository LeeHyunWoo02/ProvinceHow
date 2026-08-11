package SDD.smash.recommendation.application.port.out;

import SDD.smash.recommendation.application.dto.RegionDetailInfo;

/**
 * 지역 상세 정보를 외부 AI 에 넘겨 사람이 읽을 요약문을 받아오는 out-port.
 *
 * <p>포트 위치 근거는 {@link RegionPickProvider} 와 같다
 * (입력이 application DTO 이고, AI 요약은 도메인 규칙이 아니다).
 *
 * <p><b>장애 정책 — 반드시 지켜야 한다.</b>
 * 구현체는 <b>예외를 던지지 않고 {@code null} 을 반환</b>한다.
 * AI 호출이 실패해도 상세 응답은 그대로 내려가고 {@code aiSummary} 만 비는 것이 As-Is 동작이다.
 */
public interface RegionSummaryProvider {

    /**
     * @return 요약문. 실패 시 <b>{@code null}</b>.
     */
    String summarize(RegionDetailInfo detail);
}
