package net.crystallized.pvp.staff;

import java.util.UUID;

public class Punishment {
    public enum Type { BAN, MUTE, WARN, KICK }

    public int id;
    public Type type;
    public UUID target;
    public String targetName;
    public String staff;
    public String reason;
    public long created;
    public long expires = -1;   // -1 = permanent
    public boolean active;

    public boolean isExpired() {
        return expires > 0 && System.currentTimeMillis() >= expires;
    }

    /** Only meaningful for BAN / MUTE. */
    public boolean isActive() {
        return active && !isExpired();
    }
}
