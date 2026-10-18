package net.crystallized.pvp.staff;

public enum Rank {
    MEMBER(0), HELPER(1), MOD(2), ADMIN(3), OWNER(4);

    public final int level;

    Rank(int level) {
        this.level = level;
    }

    public static Rank parse(String s) {
        for (Rank r : values()) {
            if (r.name().equalsIgnoreCase(s)) return r;
        }
        return null;
    }
}
