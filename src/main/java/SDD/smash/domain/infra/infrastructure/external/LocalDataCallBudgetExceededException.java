package SDD.smash.domain.infra.infrastructure.external;

/**
 * 하루 호출 예산을 다 썼다는 신호. <b>오류가 아니라 "오늘은 여기까지"다.</b>
 *
 * <p>일시적 오류(타임아웃·5xx)와 구분하기 위해 별도 타입으로 둔다. 일시적 오류는 재시도하고
 * 실행 내 2차 패스에도 올리지만, 예산 소진은 재시도해도 결과가 같으므로 <b>수집 Step 을
 * 정상 종료</b>시키는 근거가 된다. 남은 대상은 staging 체크포인트에 미완료로 남아
 * 다음 실행이 이어받는다.
 */
public class LocalDataCallBudgetExceededException extends LocalDataApiException {

    public LocalDataCallBudgetExceededException(String message) {
        super(message);
    }
}
