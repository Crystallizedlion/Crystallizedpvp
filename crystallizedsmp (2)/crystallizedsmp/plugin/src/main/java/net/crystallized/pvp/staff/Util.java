package net.crystallized.pvp.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class Util {
    private Util() {}

    /** Converts &-codes to section-sign codes without using the legacy ChatColor class. */
    public static String color(String s) {
        char[] b = s.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(b[i + 1]) > -1) {
                b[i] = '\u00a7';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    public static void broadcast(String s) {
        Bukkit.broadcast(component(s));
    }

    /** Times are in ticks. */
    public static void title(Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        p.showTitle(Title.title(component(title), component(subtitle),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L))));
    }

    public static Component component(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(color(s));
    }

    /** Returns millis, -1 for permanent, -2 if invalid. Accepts 30s, 15m, 12h, 7d, 2w, perm. */
    public static long parseDuration(String s) {
        if (s.equalsIgnoreCase("perm") || s.equalsIgnoreCase("permanent")) return -1;
        try {
            long n = Long.parseLong(s.substring(0, s.length() - 1));
            long mult = switch (Character.toLowerCase(s.charAt(s.length() - 1))) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> -1L;
            };
            if (mult < 0 || n <= 0) return -2;
            return n * mult;
        } catch (Exception e) {
            return -2;
        }
    }

    public static String formatDuration(long ms) {
        long s = Math.max(ms, 0) / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (d == 0 && h == 0 && m == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
