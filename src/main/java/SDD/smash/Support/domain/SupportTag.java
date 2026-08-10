package SDD.smash.Support.domain;

<<<<<<< HEAD
import SDD.smash.Infra.Entity.Major;

import java.util.EnumSet;

=======
>>>>>>> origin/Backup/main
/**
 * 정책태그(
 */
public enum SupportTag {
<<<<<<< HEAD
    HOUSING_SUPPORT("주거지원", 1<<3),
    LONG_TERM_UNEMPLOYED_YOUTH("장기미취업청년", 1<<2),
    INTERN("인턴", 1<<1),
    LOAN("대출", 1);

    SupportTag(String value, int bit) {
        this.value = value;
        this.bit = bit;
=======
    HOUSING_SUPPORT("주거지원"),
    LONG_TERM_UNEMPLOYED_YOUTH("장기미취업청년"),
    INTERN("인턴"),
    LOAN("대출");

    SupportTag(String value) {
        this.value = value;
>>>>>>> origin/Backup/main
    }

    private final String value;

<<<<<<< HEAD
    private final int bit;

=======
>>>>>>> origin/Backup/main
    public String getValue()
    {
        return value;
    }
<<<<<<< HEAD

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
=======
>>>>>>> origin/Backup/main
}
