package net.crystallized.pvp.staff;

import net.crystallized.pvp.CrystallizedPvP;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PunishmentManager {
    private final CrystallizedPvP plugin;
    private final File file;
    private final List<Punishment> all = new ArrayList<>();
    private int nextId = 1;

    public PunishmentManager(CrystallizedPvP plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "punishments.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("punishments");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(key);
            if (c == null) continue;
            try {
                Punishment p = new Punishment();
                p.id = Integer.parseInt(key);
                p.type = Punishment.Type.valueOf(c.getString("type", "WARN"));
                p.target = UUID.fromString(c.getString("target", ""));
                p.targetName = c.getString("name", "unknown");
                p.staff = c.getString("staff", "Console");
                p.reason = c.getString("reason", "");
                p.created = c.getLong("created");
                p.expires = c.getLong("expires", -1);
                p.active = c.getBoolean("active");
                all.add(p);
                nextId = Math.max(nextId, p.id + 1);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Punishment p : all) {
            String b = "punishments." + p.id + ".";
            y.set(b + "type", p.type.name());
            y.set(b + "target", p.target.toString());
            y.set(b + "name", p.targetName);
            y.set(b + "staff", p.staff);
            y.set(b + "reason", p.reason);
            y.set(b + "created", p.created);
            y.set(b + "expires", p.expires);
            y.set(b + "active", p.active);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save punishments.yml: " + e.getMessage());
        }
    }

    public synchronized Punishment activeOf(UUID target, Punishment.Type type) {
        for (Punishment p : all) {
            if (p.target.equals(target) && p.type == type && p.isActive()) return p;
        }
        return null;
    }

    public synchronized boolean revoke(UUID target, Punishment.Type type) {
        boolean any = false;
        for (Punishment p : all) {
            if (p.target.equals(target) && p.type == type && p.isActive()) {
                p.active = false;
                any = true;
            }
        }
        if (any) save();
        return any;
    }

    /** Newest first. */
    public synchronized List<Punishment> history(UUID target) {
        List<Punishment> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).target.equals(target)) out.add(all.get(i));
        }
        return out;
    }

    public synchronized UUID findUuidByName(String name) {
        for (Punishment p : all) {
            if (p.targetName.equalsIgnoreCase(name)) return p.target;
        }
        return null;
    }

    private synchronized Punishment record(Punishment.Type type, UUID target, String targetName,
                                           String staff, String reason, long dur) {
        Punishment p = new Punishment();
        p.id = nextId++;
        p.type = type;
        p.target = target;
        p.targetName = targetName;
        p.staff = staff;
        p.reason = reason;
        p.created = System.currentTimeMillis();
        p.expires = dur > 0 ? p.created + dur : -1;
        p.active = type == Punishment.Type.BAN || type == Punishment.Type.MUTE;
        all.add(p);
        save();
        return p;
    }

    public String remaining(Punishment p) {
        return p.expires < 0 ? "Never" : Util.formatDuration(p.expires - System.currentTimeMillis());
    }

    public String banScreen(Punishment p) {
        return "&b&l" + plugin.getConfig().getString("server-name", "crystallizedsmp")
                + "\n\n&cYou are " + (p.expires < 0 ? "permanently" : "temporarily") + " banned.\n\n"
                + "&7Reason: &f" + p.reason + "\n"
                + "&7Expires in: &f" + remaining(p) + "\n"
                + "&7Ban ID: &f#" + p.id + "\n\n"
                + "&7Appeal: &f" + plugin.getConfig().getString("staff.appeal", "(set staff.appeal in config.yml)");
    }

    public String muteMessage(Punishment p) {
        return "&cYou are muted. &7Reason: &f" + p.reason + " &7| Expires in: &f" + remaining(p);
    }

    /** Records the punishment and applies it. Call from the main thread. */
    public Punishment punish(Punishment.Type type, UUID target, String targetName,
                             String staffName, String reason, long dur) {
        Punishment p = record(type, target, targetName, staffName, reason, dur);
        Player online = Bukkit.getPlayer(target);
        String len = dur > 0 ? Util.formatDuration(dur) : "permanent";

        switch (type) {
            case BAN -> {
                if (online != null) online.kick(Util.component(banScreen(p)));
            }
            case KICK -> {
                if (online != null) {
                    online.kick(Util.component("&b&l" + plugin.getConfig().getString("server-name", "crystallizedsmp")
                            + "\n\n&cYou were kicked.\n&7Reason: &f" + reason));
                }
            }
            case MUTE -> {
                if (online != null) online.sendMessage(Util.component(muteMessage(p)));
            }
            case WARN -> {
                if (online != null) {
                    online.sendMessage(Util.component("&e&lWARNING &7from &f" + staffName + "&7: &f" + reason));
                    Util.title(online, "&e&lWARNING", "&7" + reason, 10, 80, 20);
                }
            }
        }

        String verb = switch (type) {
            case BAN -> dur < 0 ? "permanently banned" : "banned for " + len;
            case MUTE -> dur < 0 ? "permanently muted" : "muted for " + len;
            case WARN -> "warned";
            case KICK -> "kicked";
        };
        plugin.staff().alert("&f" + targetName + " &7was &c" + verb + " &7by &f" + staffName + " &8| &7" + reason);
        if (plugin.getConfig().getBoolean("staff.broadcast-punishments", true)) {
            Util.broadcast(("&c" + targetName + " &7was " + verb + " by &f" + staffName
                    + "&7. Reason: &f" + reason));
        }
        return p;
    }
}
