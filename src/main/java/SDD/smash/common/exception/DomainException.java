package SDD.smash.common.exception;

import lombok.Getter;

/**
 * 도메인 규칙 위반을 나타내는 예외.
 *
 * <p>도메인·유스케이스에서 규칙이 깨졌을 때 던지는 유일한 예외 타입이다.
 * 기술 예외(JDBC, HTTP 등)는 infrastructure 어댑터가 잡아 이 예외로 번역하거나 흡수한다.
 */
@Getter
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
