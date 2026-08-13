package SDD.smash.domain.address.infrastructure.external;

/**
 * KOSIS 공유서비스 OpenAPI 호출/응답 실패.
 *
 * <p>{@code DomainException} 을 쓰지 않는다 — 이 실패는 도메인 규칙 위반이 아니라 기술 실패이고,
 * HTTP 응답으로 나가는 경로가 없어 {@code ErrorCode}/{@code ErrorCodeHttpMapper} 를 늘릴 이유가 없다.
 * {@code WorknetApiException} 과 같은 자리에 있는 예외다.
 *
 * <p><b>메시지에 인증키를 담지 않는다.</b> URL 을 메시지에 넣어야 하면 반드시 마스킹한 것을 넣는다.
 */
public class KosisApiException extends RuntimeException {

    private final String errorCode;

    public KosisApiException(String message) {
        this(message, null, null);
    }

    public KosisApiException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public KosisApiException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** KOSIS 가 200 OK 본문으로 돌려준 {@code err} 값. 없으면 {@code null}. */
    public String errorCode() {
        return errorCode;
    }
}
