package SDD.smash.domain.support.domain.model;

/**
 * 지원정책 한 건 (Aggregate). 식별자는 {@code SigunguCode} + {@link SupportTag} 다
 * (architecture-conventions §5.1) — 다만 그 식별자는 저장소 조회의 키일 뿐,
 * 이 레코드 자신은 정책 내용만 담는다({@code SupportPolicyRepository.findBy(code, tag)} 참고).
 *
 * <p>외부 API 어휘({@code plcyNm}, {@code aplyUrlAddr}, {@code plcyKywdNm})는
 * {@code infrastructure/external} 안에서 이 도메인 언어로 번역된다.
 */
public record SupportPolicy(String name, String applyUrl, String keyword) {
}
