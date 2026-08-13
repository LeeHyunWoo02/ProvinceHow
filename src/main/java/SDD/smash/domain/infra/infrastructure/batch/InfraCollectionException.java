package SDD.smash.domain.infra.infrastructure.batch;

/**
 * 스냅샷을 완성하지 못했다.
 *
 * <p>이 예외가 올라가면 Step 이 FAILED 로 끝나고 {@code infra} 테이블에는 아무것도 쓰이지 않는다.
 * <b>부분 스냅샷을 반영하지 않는 것</b>이 목적이다 — 일부 업종·지역만 갱신되면
 * 시군구 내 구성비(ratio)와 업종별 전국 백분위(score)가 서로 다른 기준으로 섞인다.
 */
public class InfraCollectionException extends RuntimeException {

    public InfraCollectionException(String message) {
        super(message);
    }

    public InfraCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
