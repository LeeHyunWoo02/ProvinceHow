package SDD.smash.Exception.Code;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // address
    ADDRESS_CODE_NOT_FOUND("ADDRESS_CODE_NOT_FOUND", HttpStatus.NOT_FOUND),
    //end

    // job
    JOB_CODE_NOT_FOUND("JOB_CODE_NOT_FOUND", HttpStatus.NOT_FOUND),
    //end

    // dwelling
    PRICE_AMOUNT_NOT_VALID("PRICE_AMOUNT_NOT_VALID", HttpStatus.BAD_REQUEST),
    NOT_FOUND_YEARMONTH("NOT_FOUND_YEARMONTH", HttpStatus.NOT_FOUND),
    //end

    // validation
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST),
    BIND_FAILED("BIND_FAILED", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON("MALFORMED_JSON", HttpStatus.BAD_REQUEST),

    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    //end

    //openai
    OPENAI_SERVER_ERROR("OPENAI_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
    OPENAI_TOKEN_EXPIRED("OPENAI_TOKEN_EXPIRED", HttpStatus.TOO_MANY_REQUESTS);



    public final String code;
    public final HttpStatus status;

    ErrorCode(String code, HttpStatus httpStatus)
    {
        this.code = code;
        this.status = httpStatus;
    }
}
