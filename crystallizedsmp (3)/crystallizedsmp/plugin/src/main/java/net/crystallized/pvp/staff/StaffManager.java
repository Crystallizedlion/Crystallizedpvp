package net.crystallized.pvp.staff;

import net.crystallized.pvp.CrystallizedPvP;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffManager {
    private final CrystallizedPvP plugin;
    private final File file;
    private final Map<UUID, Rank> ranks = new ConcurrentHashMap<>();

    public final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    public final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    public final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
    public volatile boolean chatMuted = false;

    public StaffManager(CrystallizedPvP plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "staff.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            try {
                Rank r = Rank.parse(y.getString(key, ""));
                if (r != null) ranks.put(UUID.fromString(key), r);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ranks.forEach((id, r) -> y.set(id.toString(), r.name()));
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save staff.yml: " + e.getMessage());
        }
    }

    private boolean opIsOwner(UUID id) {
        if (!plugin.getConfig().getBoolean("staff.op-is-owner", true)) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.isOp();
    }

    public Rank displayRank(UUID id) {
        Rank r = ranks.get(id);
        if (r != null) return r;
        return opIsOwner(id) ? Rank.OWNER : Rank.MEMBER;
    }

    public int level(UUID id) {
        return displayRank(id).level;
    }

    public int level(CommandSender s) {
        if (s instanceof Player p) return level(p.getUniqueId());
        return 99; // console / command blocks
    }

    public boolean has(CommandSender s, String action) {
        return level(s) >= plugin.getConfig().getInt("staff.required." + action, 1);
    }

    /** You can only punish players with a strictly lower rank than yours. */
    public boolean canPunish(CommandSender staff, UUID target) {
        return level(staff) > level(target);
    }

    public String prefix(Rank r) {
        String def = switch (r) {
            case OWNER -> "&4&lOWNER &c";
            case ADMIN -> "&c&lADMIN &c";
            case MOD -> "&2&lMOD &a";
            case HELPER -> "&9&lHELPER &b";
            case MEMBER -> "&7";
        };
        return plugin.getConfig().getString("staff.prefixes." + r.name(), def);
    }

    public void setRank(UUID id, Rank r) {
        if (r == Rank.MEMBER) ranks.remove(id);
        else ranks.put(id, r);
        save();
        Player online = Bukkit.getPlayer(id);
        if (online != null) refreshDisplay(online);
    }

    public void refreshDisplay(Player p) {
        p.playerListName(Util.component(prefix(displayRank(p.getUniqueId())) + p.getName()));
    }

    public void alert(String msg) {
        Component full = Util.component("&8[&bStaff&8] &7" + msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (level(p) >= 1) p.sendMessage(full);
        }
        Bukkit.getConsoleSender().sendMessage(full);
    }

    public void sendStaffChat(String from, String message) {
        Component full = Util.component("&8[&dStaffChat&8] &7" + from + "&8: &f").append(Component.text(message));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (level(p) >= 1) p.sendMessage(full);
        }
        Bukkit.getConsoleSender().sendMessage(full);
    }
}
