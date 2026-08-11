package SDD.smash.Infra.Entity;

import java.util.EnumSet;

public enum Major {
    HEALTH(1<<3),
    FOOD(1<<2),
    CULTURE(1<<1),
    LIFE(1);

    private final int bit;

    Major(int bit)
    {
        this.bit = bit;
    }

    public int bit()
    {
        return bit;
    }

    public static EnumSet<Major> fromChoiceMask(int mask)
    {
        EnumSet<Major> set = EnumSet.noneOf(Major.class);
        for(Major m : Major.values())
        {
            if((mask & m.bit) != 0) set.add(m);
        }
        return set;
    }
}
