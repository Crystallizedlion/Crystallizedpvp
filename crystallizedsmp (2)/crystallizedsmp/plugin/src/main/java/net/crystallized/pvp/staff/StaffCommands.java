package net.crystallized.pvp.staff;

import net.crystallized.pvp.CrystallizedPvP;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

public class StaffCommands implements CommandExecutor, TabCompleter {

    private record Target(UUID id, String name) {}

    private final CrystallizedPvP plugin;
    private final StaffManager sm;
    private final PunishmentManager pm;
    private final PunishGUI gui;
    private final Map<UUID, Long> reportCooldown = new HashMap<>();

    public StaffCommands(CrystallizedPvP plugin, PunishGUI gui) {
        this.plugin = plugin;
        this.sm = plugin.staff();
        this.pm = plugin.punishments();
        this.gui = gui;
    }

    // ---------------------------------------------------------------- helpers

    private void msg(CommandSender s, String m) {
        s.sendMessage(Util.component(m));
    }

    private boolean need(CommandSender s, String action) {
        if (sm.has(s, action)) return true;
        msg(s, "&cYou don't have permission to do that.");
        return false;
    }

    private String staffName(CommandSender s) {
        return s instanceof Player p ? p.getName() : "Console";
    }

    private String reason(String[] a, int from) {
        if (a.length <= from) return plugin.getConfig().getString("staff.default-reason", "No reason given");
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private Target resolve(CommandSender s, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return new Target(online.getUniqueId(), online.getName());
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        if (off != null) return new Target(off.getUniqueId(), off.getName() != null ? off.getName() : name);
        UUID fromRecords = pm.findUuidByName(name);
        if (fromRecords != null) return new Target(fromRecords, name);
        msg(s, "&cUnknown player. They must have joined the server before.");
        return null;
    }

    // ---------------------------------------------------------------- dispatch

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase()) {
            case "staff": return staffCmd(s, a);
            case "ban": return punishCmd(s, label, a, Punishment.Type.BAN, false);
            case "tempban": return punishCmd(s, label, a, Punishment.Type.BAN, true);
            case "mute": return punishCmd(s, label, a, Punishment.Type.MUTE, false);
            case "tempmute": return punishCmd(s, label, a, Punishment.Type.MUTE, true);
            case "kick": return punishCmd(s, label, a, Punishment.Type.KICK, false);
            case "warn": return punishCmd(s, label, a, Punishment.Type.WARN, false);
            case "unban": return revokeCmd(s, label, a, Punishment.Type.BAN);
            case "unmute": return revokeCmd(s, label, a, Punishment.Type.MUTE);
            case "history": return historyCmd(s, a);
            case "punish": return punishGuiCmd(s, a);
            case "freeze": return freezeCmd(s, a);
            case "vanish": return vanishCmd(s);
            case "sc": return staffChatCmd(s, a);
            case "invsee": return invseeCmd(s, a);
            case "clearchat": return clearChatCmd(s);
            case "mutechat": return muteChatCmd(s);
            case "report": return reportCmd(s, a);
            default: return false;
        }
    }

    // ------------------------------------------------------------ /staff ...

    private boolean staffCmd(CommandSender s, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            msg(s, "&8--- &bStaff online &8---");
            boolean any = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sm.level(p) >= 1 && !sm.vanished.contains(p.getUniqueId())) {
                    msg(s, sm.prefix(sm.displayRank(p.getUniqueId())) + p.getName());
                    any = true;
                }
            }
            if (!any) msg(s, "&7Nobody right now.");
            return true;
        }
        if (!need(s, "staffmanage")) return true;

        if (a[0].equalsIgnoreCase("set") && a.length >= 3) {
            Target t = resolve(s, a[1]);
            if (t == null) return true;
            Rank r = Rank.parse(a[2]);
            if (r == null) {
                msg(s, "&cRanks: member, helper, mod, admin, owner");
                return true;
            }
            sm.setRank(t.id(), r);
            msg(s, "&a" + t.name() + " is now " + r.name() + ".");
            sm.alert("&f" + t.name() + " &7was set to &b" + r.name() + " &7by &f" + staffName(s));
            return true;
        }
        if (a[0].equalsIgnoreCase("remove") && a.length >= 2) {
            Target t = resolve(s, a[1]);
            if (t == null) return true;
            sm.setRank(t.id(), Rank.MEMBER);
            msg(s, "&a" + t.name() + " is no longer staff.");
            return true;
        }
        msg(s, "&cUsage: /staff <list | set <player> <rank> | remove <player>>");
        return true;
    }

    // ---------------------------------------------- ban / mute / kick / warn

    private boolean punishCmd(CommandSender s, String label, String[] a, Punishment.Type type, boolean timed) {
        boolean permanent = !timed && (type == Punishment.Type.BAN || type == Punishment.Type.MUTE);
        String action = switch (type) {
            case BAN -> permanent ? "permban" : "ban";
            case MUTE -> permanent ? "permmute" : "mute";
            case WARN -> "warn";
            case KICK -> "kick";
        };
        if (!need(s, action)) return true;

        int min = timed ? 2 : (type == Punishment.Type.WARN ? 2 : 1);
        if (a.length < min) {
            msg(s, "&cUsage: /" + label + " <player>" + (timed ? " <duration>" : "")
                    + (type == Punishment.Type.WARN ? " <reason>" : " [reason]"));
            return true;
        }

        Target t = resolve(s, a[0]);
        if (t == null) return true;
        if (!sm.canPunish(s, t.id())) {
            msg(s, "&cYou can't punish someone of the same or higher rank.");
            return true;
        }

        long dur = 0;
        if (timed) {
            dur = Util.parseDuration(a[1]);
            if (dur <= 0) {
                msg(s, "&cInvalid duration. Examples: 30m, 12h, 7d, 2w (use /ban or /mute for permanent).");
                return true;
            }
        } else if (permanent) {
            dur = -1;
        }

        if (type == Punishment.Type.BAN && pm.activeOf(t.id(), Punishment.Type.BAN) != null) {
            msg(s, "&c" + t.name() + " is already banned.");
            return true;
        }
        if (type == Punishment.Type.MUTE && pm.activeOf(t.id(), Punishment.Type.MUTE) != null) {
            msg(s, "&c" + t.name() + " is already muted.");
            return true;
        }
        if (type == Punishment.Type.KICK && Bukkit.getPlayer(t.id()) == null) {
            msg(s, "&c" + t.name() + " isn't online.");
            return true;
        }

        pm.punish(type, t.id(), t.name(), staffName(s), reason(a, timed ? 2 : 1), dur);
        return true;
    }

    private boolean revokeCmd(CommandSender s, String label, String[] a, Punishment.Type type) {
        if (!need(s, type == Punishment.Type.BAN ? "unban" : "unmute")) return true;
        if (a.length < 1) {
            msg(s, "&cUsage: /" + label + " <player>");
            return true;
        }
        Target t = resolve(s, a[0]);
        if (t == null) return true;
        if (!pm.revoke(t.id(), type)) {
            msg(s, "&c" + t.name() + " isn't " + (type == Punishment.Type.BAN ? "banned." : "muted."));
            return true;
        }
        sm.alert("&f" + t.name() + " &7was &a" + (type == Punishment.Type.BAN ? "unbanned" : "unmuted")
                + " &7by &f" + staffName(s));
        Player online = Bukkit.getPlayer(t.id());
        if (online != null && type == Punishment.Type.MUTE) msg(online, "&aYou have been unmuted.");
        return true;
    }

    private boolean historyCmd(CommandSender s, String[] a) {
        if (!need(s, "history")) return true;
        if (a.length < 1) {
            msg(s, "&cUsage: /history <player>");
            return true;
        }
        Target t = resolve(s, a[0]);
        if (t == null) return true;
        List<Punishment> list = pm.history(t.id());
        if (list.isEmpty()) {
            msg(s, "&7" + t.name() + " has a clean record.");
            return true;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yy HH:mm");
        msg(s, "&8--- &bHistory: &f" + t.name() + " &7(" + list.size() + ") &8---");
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Punishment p = list.get(i);
            String status = (p.type == Punishment.Type.BAN || p.type == Punishment.Type.MUTE)
                    ? (p.isActive() ? "&cACTIVE" : "&7ended") : "&7-";
            msg(s, "&8#" + p.id + " &e" + p.type + " &f" + p.reason + " &8by &7" + p.staff
                    + " &8(" + fmt.format(new Date(p.created)) + ") " + status);
        }
        if (list.size() > 10) msg(s, "&7...and " + (list.size() - 10) + " older.");
        return true;
    }

    // ---------------------------------------------------------------- GUI etc.

    private boolean punishGuiCmd(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            msg(s, "Players only.");
            return true;
        }
        if (!need(s, "punish")) return true;
        if (a.length < 1) {
            msg(s, "&cUsage: /punish <player>");
            return true;
        }
        Target t = resolve(s, a[0]);
        if (t == null) return true;
        if (!sm.canPunish(s, t.id())) {
            msg(s, "&cYou can't punish someone of the same or higher rank.");
            return true;
        }
        gui.open(p, t.id(), t.name());
        return true;
    }

    private boolean freezeCmd(CommandSender s, String[] a) {
        if (!need(s, "freeze")) return true;
        if (a.length < 1) {
            msg(s, "&cUsage: /freeze <player>");
            return true;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            msg(s, "&cThat player isn't online.");
            return true;
        }
        if (!sm.canPunish(s, t.getUniqueId())) {
            msg(s, "&cYou can't freeze someone of the same or higher rank.");
            return true;
        }
        if (sm.frozen.remove(t.getUniqueId())) {
            t.clearTitle();
            msg(t, "&aYou have been unfrozen.");
            sm.alert("&f" + t.getName() + " &7was &aunfrozen &7by &f" + staffName(s));
        } else {
            sm.frozen.add(t.getUniqueId());
            Util.title(t, "&c&lFROZEN", "&7Do not log out. Wait for staff.", 0, 72000, 0);
            msg(t, "&cYou have been frozen by staff. Do not log out.");
            sm.alert("&f" + t.getName() + " &7was &bfrozen &7by &f" + staffName(s));
        }
        return true;
    }

    private boolean vanishCmd(CommandSender s) {
        if (!(s instanceof Player p)) {
            msg(s, "Players only.");
            return true;
        }
        if (!need(s, "vanish")) return true;
        UUID id = p.getUniqueId();
        if (sm.vanished.remove(id)) {
            for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, p);
            msg(p, "&aYou are now visible.");
        } else {
            sm.vanished.add(id);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (sm.level(o) < 1) o.hidePlayer(plugin, p);
            }
            msg(p, "&bYou are now vanished.");
        }
        return true;
    }

    private boolean staffChatCmd(CommandSender s, String[] a) {
        if (!need(s, "staffchat")) return true;
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                msg(s, "&cUsage: /sc <message>");
                return true;
            }
            if (sm.staffChat.remove(p.getUniqueId())) msg(p, "&7Staff chat: &cOFF");
            else {
                sm.staffChat.add(p.getUniqueId());
                msg(p, "&7Staff chat: &aON &7(all your messages go to staff)");
            }
            return true;
        }
        sm.sendStaffChat(staffName(s), String.join(" ", a));
        return true;
    }

    private boolean invseeCmd(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            msg(s, "Players only.");
            return true;
        }
        if (!need(s, "invsee")) return true;
        if (a.length < 1) {
            msg(s, "&cUsage: /invsee <player>");
            return true;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            msg(s, "&cThat player isn't online.");
            return true;
        }
        p.openInventory(t.getInventory());
        return true;
    }

    private boolean clearChatCmd(CommandSender s) {
        if (!need(s, "clearchat")) return true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sm.level(p) < 1) {
                for (int i = 0; i < 100; i++) p.sendMessage(Component.empty());
            }
        }
        Util.broadcast("&bChat was cleared by &f" + staffName(s));
        return true;
    }

    private boolean muteChatCmd(CommandSender s) {
        if (!need(s, "mutechat")) return true;
        sm.chatMuted = !sm.chatMuted;
        Util.broadcast(sm.chatMuted
                ? "&cChat has been muted by &f" + staffName(s)
                : "&aChat has been unmuted by &f" + staffName(s));
        return true;
    }

    private boolean reportCmd(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            msg(s, "Players only.");
            return true;
        }
        if (a.length < 2) {
            msg(s, "&cUsage: /report <player> <reason>");
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = reportCooldown.get(p.getUniqueId());
        if (last != null && now - last < 30_000) {
            msg(p, "&cPlease wait before sending another report.");
            return true;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            msg(p, "&cThat player isn't online.");
            return true;
        }
        reportCooldown.put(p.getUniqueId(), now);
        sm.alert("&c[REPORT] &f" + p.getName() + " &7reported &f" + t.getName() + "&7: &f" + reason(a, 1));
        msg(p, "&aReport sent to staff. Thank you!");
        return true;
    }

    // ------------------------------------------------------------- tab complete

    private List<String> filter(Collection<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(prefix.toLowerCase())) out.add(o);
        }
        return out;
    }

    private List<String> players(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!sm.vanished.contains(p.getUniqueId())) names.add(p.getName());
        }
        return filter(names, prefix);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase();
        if (List.of("vanish", "sc", "clearchat", "mutechat").contains(n)) return List.of();
        if (n.equals("staff")) {
            if (a.length == 1) return filter(List.of("list", "set", "remove"), a[0]);
            if (a.length == 2) return players(a[1]);
            if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
                return filter(List.of("member", "helper", "mod", "admin", "owner"), a[2]);
            }
            return List.of();
        }
        if (a.length == 1) return players(a[0]);
        if ((n.equals("tempban") || n.equals("tempmute")) && a.length == 2) {
            return filter(List.of("30m", "1h", "12h", "1d", "7d", "30d"), a[1]);
        }
        return List.of();
    }
}
