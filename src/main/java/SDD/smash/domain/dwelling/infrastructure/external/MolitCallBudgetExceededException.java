package SDD.smash.domain.dwelling.infrastructure.external;

/**
 * 하루 호출 예산을 다 썼다는 신호. <b>오류가 아니라 "오늘은 여기까지"다.</b>
 *
 * <p>{@link MolitApiException} 을 상속하므로 {@code fetchMonth} 의 관대 경로에서
 * {@code UNDETERMINED}(판정 불가)로 흘러간다 — <b>확정 0건으로 떨어지면 안 된다.</b>
 * 확정 0건이 되면 "실거래가 없는 지역"이라는 뜻이 되어 평균·중앙값이 조용히 왜곡된다.
 */
public class MolitCallBudgetExceededException extends MolitApiException {

    public MolitCallBudgetExceededException(String message) {
        super(message);
    }
}
