package SDD.smash.infra.domain.model;

import java.util.EnumSet;

/**
 * 인프라 대분류. As-Is 를 그대로 옮긴 것이다.
 *
 * <p>비트마스크로 여러 항목을 한 정수(0~15)에 담을 수 있어 사용자의 "인프라 선택"을
 * 하나의 {@code infraChoice} 값으로 표현한다. 4비트라 유효 마스크가 16개뿐이며,
 * 이 유한함이 캐시 키 카디널리티를 제한한다({@code InfraScoreCache} 참고).
 */
public enum Major {
    HEALTH(1 << 3),
    FOOD(1 << 2),
    CULTURE(1 << 1),
    LIFE(1);

    private final int bit;

    Major(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static EnumSet<Major> fromChoiceMask(int mask) {
        EnumSet<Major> set = EnumSet.noneOf(Major.class);
        for (Major m : Major.values()) {
            if ((mask & m.bit) != 0) set.add(m);
        }
        return set;
    }
}
