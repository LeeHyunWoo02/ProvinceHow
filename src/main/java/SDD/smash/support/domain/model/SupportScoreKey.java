package SDD.smash.support.domain.model;

/**
 * 지원정책 점수 캐시의 도메인 식별자.
 *
 * <p>As-Is 는 {@code supportChoice} 정수(사용자가 고른 태그의 비트마스크, 0~15)를 키에 그대로 썼다.
 * infra 의 {@code InfraScoreKey} 와 같은 구조다.
 */
public record SupportScoreKey(Integer supportChoice) {

    public static SupportScoreKey of(Integer supportChoice) {
        return new SupportScoreKey(supportChoice);
    }
}
