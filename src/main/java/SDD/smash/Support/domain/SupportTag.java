package SDD.smash.Support.domain;

import SDD.smash.Infra.Entity.Major;

import java.util.EnumSet;

/**
 * 정책태그(
 */
public enum SupportTag {
    HOUSING_SUPPORT("주거지원", 1<<3),
    LONG_TERM_UNEMPLOYED_YOUTH("장기미취업청년", 1<<2),
    INTERN("인턴", 1<<1),
    LOAN("대출", 1);

    SupportTag(String value, int bit) {
        this.value = value;
        this.bit = bit;
    }

    private final String value;

    private final int bit;

    public String getValue()
    {
        return value;
    }

    public int bit()
    {
        return bit;
    }

    public static EnumSet<SupportTag> fromChoiceMask(int mask)
    {
        EnumSet<SupportTag> set = EnumSet.noneOf(SupportTag.class);
        for(SupportTag m : SupportTag.values())
        {
            if((mask & m.bit) != 0) set.add(m);
        }
        return set;
    }
}
