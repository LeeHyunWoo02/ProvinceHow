package SDD.smash.support.application.port.in;

/**
 * 지원정책 원본 갱신 in-port. 외부 청년정책 API 를 전 시군구 × 전 태그에 대해 호출해
 * {@code SupportPolicyRepository} 를 다시 채우고, 끝나면 파생 점수 캐시를 무효화한다.
 */
public interface RefreshSupportPolicyUseCase {

    void refreshAll();
}
