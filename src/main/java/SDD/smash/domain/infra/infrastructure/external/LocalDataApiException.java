package SDD.smash.domain.infra.infrastructure.external;

/**
 * 인허가 데이터 수집 실패.
 *
 * <p>이 예외가 나면 해당 (업종, 자치단체) 조합의 수집이 <b>완료되지 않은 것</b>이며,
 * 스냅샷을 부분 반영하지 않기 위해 배치가 적재를 포기한다.
 *
 * <p>메시지에 인증키나 응답 본문을 넣지 않는다.
 */
public class LocalDataApiException extends RuntimeException {

    public LocalDataApiException(String message) {
        super(message);
    }

    public LocalDataApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
