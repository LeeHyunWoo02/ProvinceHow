package SDD.smash.dwelling.domain.model;

import SDD.smash.common.domain.model.Money;
import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 주거 유형. 유형마다 예산의 유효 구간과 감점 단위가 다르다.
 *
 * <p>As-Is {@code DwellingScoreSerivce.validPrice()} 의
 * {@code if (type == MONTHLY) ... else ...} 분기가 이 enum 안으로 들어와 분기 자체가 사라졌다.
 * 상한값(월세 110만원, 전세 21000만원)은 "이상"의 의미를 갖는다.
 */
public enum DwellingType {

    /** 월세. 20만원 ~ 110만원 이상, 10만원 단위 */
    MONTHLY(Money.of(20), Money.of(110), Money.of(10)),

    /** 전세. 3000만원 ~ 21000만원 이상, 3000만원 단위 */
    JEONSE(Money.of(3_000), Money.of(21_000), Money.of(3_000));

    private final Money lowerBound;
    private final Money upperBound;
    private final Money step;

    DwellingType(Money lowerBound, Money upperBound, Money step) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.step = step;
    }

    /**
     * 사용자 예산을 이 유형의 유효 구간으로 보정한다.
     * 단위로 반올림한 뒤 하한·상한으로 자른다.
     *
     * <p>결과가 유한한 값 집합이 되므로 캐시 키의 카디널리티가 제한된다.
     */
    public Money normalize(Money budget) {
        if (budget == null) {
            throw new DomainException(ErrorCode.PRICE_AMOUNT_NOT_VALID, "가격이 입력되지 않았습니다.");
        }
        int unit = step.manwon();
        int adjusted = (int) (Math.round(budget.manwon() / (double) unit) * unit);
        return Money.of(Math.max(lowerBound.manwon(), Math.min(adjusted, upperBound.manwon())));
    }

    /**
     * {@link #normalize} 가 만들어낼 수 있는 예산 값 전부.
     *
     * <p>구간화 규칙은 도메인 지식이므로 값 목록도 여기서 만든다.
     * 캐시 어댑터가 무효화 대상 키를 열거할 때 쓴다 — 그래야 {@code KEYS} 패턴 스캔이 필요 없다.
     */
    public List<Money> allNormalizedBudgets() {
        List<Money> budgets = new ArrayList<>();
        for (int value = lowerBound.manwon(); value <= upperBound.manwon(); value += step.manwon()) {
            budgets.add(Money.of(value));
        }
        return budgets;
    }

    /** 이 유형의 감점 단위 */
    public Money step() {
        return step;
    }

    /** 이 유형의 예산 상한. "이 값 이상"을 뜻한다. */
    public Money upperBound() {
        return upperBound;
    }

    /** 이 유형의 예산 하한 */
    public Money lowerBound() {
        return lowerBound;
    }
}
