package SDD.smash.common.exception.handler;

import SDD.smash.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * ErrorCode → HttpStatus 매핑. HTTP 지식은 이 클래스에만 존재한다.
 *
 * <p>매핑이 없는 코드는 500 으로 떨어지므로, ErrorCode 를 추가하면 여기도 함께 추가한다.
 */
final class ErrorCodeHttpMapper {

    private static final Map<ErrorCode, HttpStatus> STATUS = new EnumMap<>(ErrorCode.class);

    static {
        STATUS.put(ErrorCode.ADDRESS_CODE_NOT_FOUND, HttpStatus.NOT_FOUND);
        STATUS.put(ErrorCode.JOB_CODE_NOT_FOUND, HttpStatus.NOT_FOUND);
        STATUS.put(ErrorCode.NOT_FOUND_YEARMONTH, HttpStatus.NOT_FOUND);

        STATUS.put(ErrorCode.PRICE_AMOUNT_NOT_VALID, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.SCORE_OUT_OF_RANGE, HttpStatus.BAD_REQUEST);

        STATUS.put(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.BIND_FAILED, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.MALFORMED_JSON, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED);
        STATUS.put(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        STATUS.put(ErrorCode.OPENAI_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        STATUS.put(ErrorCode.OPENAI_TOKEN_EXPIRED, HttpStatus.TOO_MANY_REQUESTS);
    }

    private ErrorCodeHttpMapper() {
    }

    static HttpStatus of(ErrorCode code) {
        return STATUS.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
