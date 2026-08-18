package SDD.smash.domain.job.infrastructure.external;

/**
 * 사람인 채용정보 API 호출/응답 실패.
 *
 * <p>워크넷 {@code WorknetApiException} 과 같은 정책이다. {@code DomainException} 을 쓰지 않는다 —
 * 이 실패는 도메인 규칙 위반이 아니라 기술 실패이고, HTTP 응답으로 나가는 경로가 없어
 * {@code ErrorCode}/{@code ErrorCodeHttpMapper} 를 늘릴 이유가 없다.
 * 배치의 fault-tolerant 처리와 어댑터 내부 재시도가 이 예외를 본다.
 *
 * <p><b>메시지에 access-key 를 담지 않는다.</b>
 */
public class SaraminApiException extends RuntimeException {

    public SaraminApiException(String message) {
        super(message);
    }

    public SaraminApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
