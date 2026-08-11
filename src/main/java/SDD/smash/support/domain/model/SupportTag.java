package SDD.smash.support.domain.model;

import java.util.EnumSet;

/**
 * 지원정책 태그. As-Is 를 그대로 옮긴 것이다(architecture-conventions §5.3 — "기존 SupportTag(유지)").
 *
 * <p>비트마스크로 여러 태그를 한 정수(0~15)에 담을 수 있어 사용자의 "지원정책 선택"을
 * 하나의 {@code supportChoice} 값으로 표현한다. 4비트라 유효 마스크가 16개뿐이며,
 * 이 유한함이 캐시 키 카디널리티를 제한한다({@code SupportScoreCache} 참고).
 *
 * <p>옛 {@code SDD.smash.legacy.support.domain.SupportTag} 와 값·비트가 완전히 같다.
 * 그 파일은 {@code Apis} 가 계속 쓰므로 남겨뒀고, 여기 새 사본이 도메인 모델의 정본이다.
 */
public enum SupportTag {
    HOUSING_SUPPORT("주거지원", 1 << 3),
    LONG_TERM_UNEMPLOYED_YOUTH("장기미취업청년", 1 << 2),
    INTERN("인턴", 1 << 1),
    LOAN("대출", 1);

    private final String value;
    private final int bit;

    SupportTag(String value, int bit) {
        this.value = value;
        this.bit = bit;
    }

    public String getValue() {
        return value;
    }

    public int bit() {
        return bit;
    }

    public static EnumSet<SupportTag> fromChoiceMask(int mask) {
        EnumSet<SupportTag> set = EnumSet.noneOf(SupportTag.class);
        for (SupportTag m : SupportTag.values()) {
            if ((mask & m.bit) != 0) set.add(m);
        }
        return set;
    }
}
