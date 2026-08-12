package SDD.smash.global.exception.handler;

/**
 * 오류 응답 계약. 성공 응답에는 공통 봉투를 쓰지 않고 오류만 이 형식을 쓴다.
 *
 * @param code    {@link SDD.smash.global.exception.ErrorCode} 의 이름
 * @param message 클라이언트에 보여줄 문구. 내부 예외 메시지를 그대로 담지 않는다.
 */
public record ErrorResponse(String code, String message) {
}
