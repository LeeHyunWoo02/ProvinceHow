package SDD.smash.domain.recommendation.application.port.out;

import SDD.smash.domain.recommendation.application.dto.RegionPick;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;

import java.util.List;

/**
 * 추천 목록을 외부 AI 에 넘겨 "AI 픽"을 받아오는 out-port.
 *
 * <p><b>왜 {@code domain/port} 가 아니라 {@code application/port/out} 인가</b>
 * — architecture-conventions §3 참고.
 * <ul>
 *   <li>입력이 {@code RegionRecommendation}(여러 컨텍스트 조합 결과 = application DTO)이다.
 *       이 시그니처를 {@code domain/port} 에 두면 <b>domain → application 역방향 의존</b>이
 *       생겨 §4 표를 더 크게 위반한다.</li>
 *   <li>AI 요약·추천은 도메인 규칙이 아니다. {@code recommendation} 컨텍스트는
 *       Aggregate 가 없는 조합 전용 컨텍스트이며(§2 표), AI 결과는 응답을 꾸미는
 *       부가 정보다. 이걸 담기 위해 도메인 타입을 새로 만드는 것은
 *       존재하지 않는 도메인 개념을 발명하는 일이다.</li>
 * </ul>
 *
 * <p><b>장애 정책 — 반드시 지켜야 한다.</b>
 * 구현체는 <b>예외를 던지지 않고 빈 목록을 반환</b>한다.
 * AI 호출이 실패해도 추천 결과 자체는 그대로 내려가야 하고
 * {@code aiPick} 만 빈 배열이 되는 것이 As-Is 동작이다.
 */
public interface RegionPickProvider {

    /**
     * @return AI 픽 목록. 실패·응답 파싱 불가 시 <b>빈 목록</b>(never null).
     */
    List<RegionPick> pick(List<RegionRecommendation> recommendations);
}
