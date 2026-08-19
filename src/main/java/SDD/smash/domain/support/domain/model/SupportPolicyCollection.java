package SDD.smash.domain.support.domain.model;

import java.util.List;

/**
 * 지원정책 한 번의 수집 결과. <b>"수집하지 못했다"와 "수집했는데 0건이다"를 구분</b>하기 위한 값 객체다.
 *
 * <p>둘을 구분하지 않으면 일시적인 실패 한 번이 데이터 손실이 된다. 실패를 빈 목록으로 돌려주던
 * 시절에는 그 빈 목록이 그대로 정본 저장소에 저장되어 멀쩡하던 정책이 지워졌다
 * ({@code 500 한 번 = 그 (시군구, 태그) 의 정책 전멸}). 그래서 수집 여부를 결과 자체가 들고 다닌다.
 *
 * <ul>
 *   <li>{@link #of(List)} — 수집에 성공했다. 목록이 비어 있으면 <b>정말 0건</b>이라는 뜻이고
 *       그대로 저장해도 된다.</li>
 *   <li>{@link #notCollected()} — 수집하지 못했다. 저장을 건너뛰고 <b>기존 데이터를 보존</b>한다.
 *       왜 못 했는지(HTTP 상태, 타임아웃)는 도메인의 관심사가 아니라 어댑터가 로그로 남긴다.</li>
 * </ul>
 */
public record SupportPolicyCollection(boolean collected, List<SupportPolicy> policies) {

    /** 수집하지 못한 결과는 정책을 담을 수 없다 — 이 불변식을 생성자에서 강제한다. */
    public SupportPolicyCollection {
        policies = (collected && policies != null) ? List.copyOf(policies) : List.of();
    }

    /** 수집에 성공했다. 빈 목록은 "정말 0건"이다. */
    public static SupportPolicyCollection of(List<SupportPolicy> policies) {
        return new SupportPolicyCollection(true, policies);
    }

    /** 수집하지 못했다. 호출자는 저장을 건너뛰어 기존 데이터를 보존해야 한다. */
    public static SupportPolicyCollection notCollected() {
        return new SupportPolicyCollection(false, List.of());
    }

    /** 수집된 정책 수. 수집 실패면 0이다(0건 수집과 값이 같으므로 {@link #collected()} 로 구분한다). */
    public int size() {
        return policies.size();
    }
}
