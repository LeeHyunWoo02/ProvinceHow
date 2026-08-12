package SDD.smash.global.exception.handler;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 최종 Fallback 핸들러가 알 수 없는 예외 발생을 처리
     * 에러 메시지는 클라이언트 노출 X + 로그 출력
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> fallback(Exception ex) {

        log.error("처리되지 않은 예외", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 에러가 발생했습니다."));
    }

    /**
     * 도메인 규칙 위반
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Object> handleDomain(DomainException ex) {
        return toResponse(ex.getErrorCode(), ex.getMessage());
    }

    /**
     * @RequestBody 바인딩 실패, validation 실패(@NotNull 등)
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        return toResponse(ErrorCode.VALIDATION_FAILED, "입력값 검증에 실패했습니다.");
    }

    /**
     * @RequestParam(required=true) 이 아예 누락된 경우.
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request)
    {
        return toResponse(ErrorCode.BIND_FAILED, "요청 파라미터 처리에 실패했습니다.");
    }

    /**
     * @RequestParam / @ModelAttribute 타입 변환 실패 또는 검증 실패.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBind(BindException ex) {
        return toResponse(ErrorCode.BIND_FAILED, "요청 파라미터 처리에 실패했습니다.");
    }

    /**
     * @PathVariable 타입 불일치 / @Validated(파라미터) 제약 위반
     * MethodArgumentTypeMismatchException: 주로 @PathVariable 타입 불일치.
     * ConstraintViolationException: 메서드 파라미터 수준의 @Validated 위반.
     */
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, ConstraintViolationException.class })
    public ResponseEntity<Object> handlePathAndConstraint(Exception ex) {
        return toResponse(ErrorCode.BIND_FAILED, "쿼리/경로 파라미터 바인딩에 실패했습니다.");
    }

    /**
     * JSON 파싱 불가/본문 읽기 실패.
     * */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return toResponse(ErrorCode.MALFORMED_JSON, "요청 본문의 형식이 올바르지 않습니다.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type 입니다.");
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(
            org.springframework.web.bind.MissingPathVariableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return toResponse(ErrorCode.BIND_FAILED, "경로 변수 처리에 실패했습니다.");
    }


    private ResponseEntity<Object> toResponse(ErrorCode code, String message) {
        return ResponseEntity.status(ErrorCodeHttpMapper.of(code))
                .body(new ErrorResponse(code.code(), message));
    }
}
