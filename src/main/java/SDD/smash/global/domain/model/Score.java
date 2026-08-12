package SDD.smash.global.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 지역 추천 점수. 0~100 범위다.
 *
 * <p>일자리·주거·인프라·지원정책 네 축이 모두 같은 척도를 쓰므로 공유 커널에 둔다.
 */
public record Score(int value) {

    public static final Score ZERO = new Score(0);
    public static final Score MAX = new Score(100);

    private static final int MIN_VALUE = 0;
    private static final int MAX_VALUE = 100;

    public Score {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new DomainException(ErrorCode.SCORE_OUT_OF_RANGE, "점수는 0~100 범위여야 합니다.");
        }
    }

    public static Score of(int value) {
        return new Score(value);
    }

    /**
     * 범위를 벗어난 값을 0~100 으로 잘라서 만든다.
     * 감점식 계산처럼 중간 결과가 음수가 될 수 있는 경로에서 쓴다.
     */
    public static Score clamped(int value) {
        return new Score(Math.max(MIN_VALUE, Math.min(MAX_VALUE, value)));
    }

    /** 두 점수를 더한다. 100 을 넘으면 100 이다. */
    public Score plus(Score other) {
        return clamped(value + other.value);
    }
}
