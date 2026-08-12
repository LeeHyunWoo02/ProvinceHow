package SDD.smash.domain.dwelling.domain.model;

import SDD.smash.global.domain.model.Money;

/**
 * 주거 점수 캐시의 도메인 식별자. 결과를 결정하는 입력을 전부 담는다.
 *
 * <p>팩토리에서 예산을 구간화하므로 키 개수가 유한하다
 * (월세 20~110/10단위 = 10개, 전세 3000~21000/3000단위 = 7개).
 * 이 보정을 빼면 키가 무한 증식한다.
 */
public record DwellingScoreKey(DwellingType type, Money normalizedBudget) {

    public static DwellingScoreKey of(DwellingType type, Money budget) {
        return new DwellingScoreKey(type, type.normalize(budget));
    }
}
