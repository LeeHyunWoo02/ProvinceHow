package SDD.smash.domain.dwelling.domain.service;

import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.Score;
import SDD.smash.domain.dwelling.domain.model.DwellingType;

/**
 * 예산과 시세 중앙값의 차이로 주거 적합도를 계산한다.
 *
 * <p>"주거비가 예산에 가까울수록 좋다"는 도메인 지식이므로 domain 에 둔다.
 * 저장소·캐시·시각에 의존하지 않는 순수 함수다.
 *
 * <p>As-Is {@code DwellingScoreSerivce.calcMonthlyScore/calcJeonseScore} 를 합친 것이다.
 * 두 메서드는 감점 단위(10 / 3000)와 상한(110 / 21000)만 달랐고, 그 차이는 이제
 * {@link DwellingType} 이 갖는다.
 */
public class DwellingScorePolicy {

    /** 단위 차이 하나당 감점 */
    private static final int PENALTY_PER_STEP = 10;

    private static final int PERFECT = 100;

    /**
     * @param median 해당 지역의 시세 중앙값. 실거래가 없으면 {@code null}
     * @param budget <b>이미 {@link DwellingType#normalize} 로 보정된</b> 예산.
     *               보정은 캐시 키({@code DwellingScoreKey})에서 한 번만 수행하고 그 값을 넘긴다
     */
    public Score score(DwellingType type, Money median, Money budget) {

        // 실거래가 없는 지역은 0점이다. 비교할 대상이 없으므로 감점이 아니라 무득점으로 다룬다.
        if (median == null) {
            return Score.ZERO;
        }

        // 예산이 상한("이 값 이상")이고 시세가 그보다 비싸면 만점이다.
        // 상한을 고른 사용자는 가격을 따지지 않겠다는 뜻이기 때문이다.
        if (budget.equals(type.upperBound()) && median.isAtLeast(budget)) {
            return Score.MAX;
        }

        int penalty = (median.diffTo(budget) / type.step().manwon()) * PENALTY_PER_STEP;
        return Score.clamped(PERFECT - penalty);
    }
}
