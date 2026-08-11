package SDD.smash.common.exception;

/**
 * 도메인이 던지는 오류의 식별자.
 *
 * <p>HttpStatus 를 갖지 않는다. 도메인은 HTTP 를 알아서는 안 되며,
 * 상태코드 매핑은 web adapter 의 관심사로
 * {@link SDD.smash.common.exception.handler.ErrorCodeHttpMapper} 가 담당한다.
 * 새 코드를 추가하면 그쪽 매핑도 함께 추가한다. 빠뜨리면 500 으로 떨어진다.
 */
public enum ErrorCode {

    // address
    ADDRESS_CODE_NOT_FOUND,
    //end

    // job
    JOB_CODE_NOT_FOUND,
    //end

    // dwelling
    PRICE_AMOUNT_NOT_VALID,
    NOT_FOUND_YEARMONTH,
    //end

    // common domain
    SCORE_OUT_OF_RANGE,
    //end

    // validation
    VALIDATION_FAILED,
    BIND_FAILED,
    MALFORMED_JSON,

    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    //end

    // openai
    OPENAI_SERVER_ERROR,
    OPENAI_TOKEN_EXPIRED,
    //end
    ;

    /** 클라이언트에 내려보내는 코드 문자열. enum 이름을 그대로 쓴다. */
    public String code() {
        return name();
    }
}
