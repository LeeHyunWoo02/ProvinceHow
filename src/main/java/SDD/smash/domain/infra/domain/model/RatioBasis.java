package SDD.smash.domain.infra.domain.model;

import java.math.BigDecimal;

/**
 * {@code infra.ratio} 를 어떤 단위로 적재할지 정하는 해석 기준.
 *
 * <h2>왜 enum 인가</h2>
 * 이 프로젝트의 코드 어디에도 {@code ratio} 를 산술·비교·포맷팅하는 지점이 없어
 * "0~1 비율"인지 "0~100 퍼센트"인지 <b>코드로 판정할 수 없다</b>. 임의로 고정하면
 * 프런트 표기와 어긋났을 때 조용히 100배 틀린 값이 나간다. 그래서 계산식을
 * 프로퍼티({@code infra.ratio.basis})로 전환 가능하게 만들고, 기본값만 정한다.
 *
 * <h2>기본값이 {@link #PERCENT} 인 근거</h2>
 * <ul>
 *   <li>컬럼이 {@code scale = 2} 다. 0~1 해석이면 유효 단계가 101개뿐이라 지나치게 거칠고,
 *       기존 배치의 {@code setScale(2, HALF_UP)} 이 정보를 잘라버린다.</li>
 *   <li>0~100 해석이면 {@code 0.00 ~ 100.00} 으로 두 자리 소수가 자연스럽다.</li>
 * </ul>
 * 둘 다 결정적 근거는 아니다. <b>원본 데이터 산출 담당자/프런트 확인 전까지는 잠정</b>이며,
 * 확정되면 이 enum 의 기본값과 {@code docs/localdata-infra.md} 를 함께 고친다.
 */
public enum RatioBasis {

    /** 0~100 퍼센트. {@code 비중 × 100} — <b>기본값</b>. */
    PERCENT(BigDecimal.valueOf(100)),

    /** 0~1 비율. 분수 그대로. */
    FRACTION(BigDecimal.ONE);

    /** 이 프로젝트가 명시적 결정 전까지 쓰는 해석. */
    public static final RatioBasis DEFAULT = PERCENT;

    private final BigDecimal multiplier;

    RatioBasis(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    /**
     * 분수(0~1)를 이 기준의 값으로 바꾼다.
     *
     * @param fraction 0 이상의 분수. {@code null} 이면 0으로 본다
     */
    public InfraRatio apply(BigDecimal fraction) {
        if (fraction == null) {
            return InfraRatio.zero();
        }
        return InfraRatio.of(fraction.multiply(multiplier));
    }
}
