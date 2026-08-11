package SDD.smash.recommendation.application.dto;

/**
 * AI 가 고른 지역 하나와 그 이유.
 *
 * <p>{@code RegionPickProvider} 의 반환 원소다. 외부 LLM 응답 스키마
 * ({@code infrastructure/external/dto/AiPick})를 어댑터 경계에서 이 타입으로 번역한다
 * — 외부 API 어휘를 계층 밖으로 내보내지 않기 위한 것이다(global-conventions §2.3).
 *
 * <p>도메인 규칙이 없는 단순 전달 값이다. AI 요약은 도메인 지식이 아니라
 * 표현 계층의 부가 기능이므로 도메인 모델로 승격하지 않는다.
 */
public record RegionPick(String sigunguCode, String reason) {
}
