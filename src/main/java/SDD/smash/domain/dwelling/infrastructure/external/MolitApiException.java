package SDD.smash.domain.dwelling.infrastructure.external;

/**
 * 국토부 실거래 API 가 "신뢰할 수 있는 응답"을 주지 않았을 때의 기술 예외.
 *
 * <p>이 예외는 <b>infrastructure 밖으로 나가지 않는다.</b> 유일한 호출 경로가 배치이고
 * 배치는 이것을 Step 실패로 다룬다. HTTP 응답으로 번역될 일이 없어
 * {@code ErrorCode} 를 새로 만들지 않았다.
 *
 * <p>메시지에 <b>인증키를 넣지 않는다.</b> 게이트웨이 오류 메시지와 사유 코드까지만 담는다.
 */
public class MolitApiException extends RuntimeException {

    public MolitApiException(String message) {
        super(message);
    }

    public MolitApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
