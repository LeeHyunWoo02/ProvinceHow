package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * {@code infra.score} 한 행의 값. <b>반드시 {@code [0, 100]}</b> 이다.
 *
 * <h2>왜 상한이 100 인가</h2>
 * 추천 경로는 {@code AVG(infra.score)} 를 {@code (시군구, 대분류)} 단위로 집계한 뒤
 * 사용자가 고른 대분류 개수 N 으로 나누고, 그 결과를 {@code Score.of(int)} 에 넣는다
 * ({@code InfraScorePolicy}). {@code Score} 는 0~100 을 벗어나면 {@code DomainException} 이다.
 * 산술평균은 언제나 구성 요소의 최댓값 이하이므로 <b>모든 행이 [0,100] 이면 N 과 무관하게 안전</b>하고,
 * N=1 이 가장 빡빡한 경우다.
 *
 * <p>DB 컬럼은 {@code decimal(6,2)} 라 100 초과도 <b>적재는 성공</b>한다. 그러면 실패가
 * 적재 시점이 아니라 추천 API 호출 시점으로 미뤄져 HTTP 400 으로 나간다. 그래서 이 값 객체가
 * 배치 Processor 단계에서 불변식을 강제한다.
 *
 * <p>소수 자릿수는 {@code scale = 2}, 반올림은 {@code HALF_UP} 이다(기존 배치 규칙과 동일).
 */
public record InfraScore(BigDecimal value) {

    public static final int SCALE = 2;
    public static final BigDecimal MIN = BigDecimal.ZERO;
    public static final BigDecimal MAX = BigDecimal.valueOf(100);

    public InfraScore {
        if (value == null) {
            throw new DomainException(ErrorCode.SCORE_OUT_OF_RANGE, "인프라 점수는 필수입니다.");
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new DomainException(ErrorCode.SCORE_OUT_OF_RANGE,
                    "인프라 점수는 0~100 범위여야 합니다.");
        }
    }

    public static InfraScore of(BigDecimal value) {
        return new InfraScore(value);
    }

    public static InfraScore of(double value) {
        if (!Double.isFinite(value)) {
            throw new DomainException(ErrorCode.SCORE_OUT_OF_RANGE, "인프라 점수가 유한한 수가 아닙니다.");
        }
        return new InfraScore(BigDecimal.valueOf(value));
    }

    public static InfraScore zero() {
        return new InfraScore(BigDecimal.ZERO);
    }
}
