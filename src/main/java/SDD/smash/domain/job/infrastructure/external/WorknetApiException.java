package SDD.smash.domain.job.infrastructure.external;

/**
 * 워크넷 채용정보 API 호출/응답 실패.
 *
 * <p>{@code DomainException} 을 쓰지 않는다 — 이 실패는 도메인 규칙 위반이 아니라 기술 실패이고,
 * HTTP 응답으로 나가는 경로가 없어 {@code ErrorCode}/{@code ErrorCodeHttpMapper} 를 늘릴 이유가 없다.
 * 배치의 fault-tolerant 처리와 어댑터 내부 재시도가 이 예외를 본다.
 *
 * <p><b>메시지에 인증키를 담지 않는다.</b>
 */
public class WorknetApiException extends RuntimeException {

    private final String messageCode;

    public WorknetApiException(String message) {
        this(message, null, null);
    }

    public WorknetApiException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public WorknetApiException(String message, String messageCode, Throwable cause) {
        super(message, cause);
        this.messageCode = messageCode;
    }

    /** 워크넷이 돌려준 {@code messageCd}. 없으면 {@code null}. */
    public String messageCode() {
        return messageCode;
    }
}
