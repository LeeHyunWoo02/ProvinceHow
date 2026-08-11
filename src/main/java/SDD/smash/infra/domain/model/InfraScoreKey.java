package SDD.smash.infra.domain.model;

/**
 * 인프라 점수 캐시의 도메인 식별자.
 *
 * <p>As-Is 는 {@code infraChoice} 정수(사용자가 고른 대분류의 비트마스크, 0~15)를 키에 그대로 썼다.
 * {@link Major#fromChoiceMask} 가 상위 비트를 무시하므로 15보다 큰 값도 들어올 수 있으나,
 * 그 경우 다른 마스크가 같은 선택 결과를 내면서도 다른 캐시 키를 쓰는 As-Is 특성을 그대로 갖는다
 * (고치지 않는다 — 동작 무변경 원칙).
 */
public record InfraScoreKey(Integer infraChoice) {

    public static InfraScoreKey of(Integer infraChoice) {
        return new InfraScoreKey(infraChoice);
    }
}
