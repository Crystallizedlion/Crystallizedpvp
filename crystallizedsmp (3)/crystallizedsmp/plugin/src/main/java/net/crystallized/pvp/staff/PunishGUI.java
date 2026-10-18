package net.crystallized.pvp.staff;

import net.crystallized.pvp.CrystallizedPvP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PunishGUI implements Listener {

    private static final int REASON_START = 10;   // slots 10-16
    private static final int MAX_REASONS = 7;

    private record Action(int slot, Material mat, String name, Punishment.Type type, long dur, String perm) {}

    private static final long H = 3_600_000L;
    private static final long D = 24 * H;

    private final List<Action> actions = List.of(
            new Action(28, Material.BOOK, "&eWarn", Punishment.Type.WARN, 0, "warn"),
            new Action(29, Material.LEATHER_BOOTS, "&6Kick", Punishment.Type.KICK, 0, "kick"),
            new Action(31, Material.ORANGE_DYE, "&6Mute 1 hour", Punishment.Type.MUTE, H, "mute"),
            new Action(32, Material.ORANGE_DYE, "&6Mute 1 day", Punishment.Type.MUTE, D, "mute"),
            new Action(33, Material.ORANGE_DYE, "&6Mute 7 days", Punishment.Type.MUTE, 7 * D, "mute"),
            new Action(37, Material.RED_DYE, "&cBan 1 day", Punishment.Type.BAN, D, "ban"),
            new Action(38, Material.RED_DYE, "&cBan 7 days", Punishment.Type.BAN, 7 * D, "ban"),
            new Action(39, Material.RED_DYE, "&cBan 30 days", Punishment.Type.BAN, 30 * D, "ban"),
            new Action(41, Material.BARRIER, "&4Permanent ban", Punishment.Type.BAN, -1, "permban")
    );

    public static class Holder implements InventoryHolder {
        final UUID targetId;
        final String targetName;
        int reason = -1;
        Inventory inv;

        Holder(UUID targetId, String targetName) {
            this.targetId = targetId;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final CrystallizedPvP plugin;

    public PunishGUI(CrystallizedPvP plugin) {
        this.plugin = plugin;
    }

    public void open(Player staff, UUID targetId, String targetName) {
        Holder h = new Holder(targetId, targetName);
        h.inv = Bukkit.createInventory(h, 54, Util.component("&8Punish &7» &f" + targetName));
        render(h);
        staff.openInventory(h.inv);
    }

    private List<String> reasons() {
        return plugin.getConfig().getStringList("staff.reasons");
    }

    private Component plain(String s) {
        return Util.component(s).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plain(name));
        if (lore != null) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(plain(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private void render(Holder h) {
        Inventory inv = h.inv;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // target head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(h.targetId));
        sm.displayName(plain("&f" + h.targetName));
        List<Component> headLore = new ArrayList<>();
        headLore.add(plain("&7Past punishments: &f" + plugin.punishments().history(h.targetId).size()));
        sm.lore(headLore);
        head.setItemMeta(sm);
        inv.setItem(4, head);

        // reasons
        List<String> reasons = reasons();
        for (int i = 0; i < Math.min(MAX_REASONS, reasons.size()); i++) {
            boolean sel = i == h.reason;
            inv.setItem(REASON_START + i, item(sel ? Material.LIME_DYE : Material.PAPER,
                    (sel ? "&a» " : "&f") + reasons.get(i),
                    List.of(sel ? "&aSelected" : "&7Click to select this reason")));
        }

        // actions
        String chosen = h.reason < 0 ? "&cnone selected" : "&f" + reasons.get(Math.min(h.reason, reasons.size() - 1));
        for (Action a : actions) {
            inv.setItem(a.slot(), item(a.mat(), a.name(), List.of("&7Reason: " + chosen, "&8Click to apply")));
        }

        inv.setItem(49, item(Material.WRITABLE_BOOK, "&bView history", List.of("&7Prints their record in chat")));
        inv.setItem(53, item(Material.ARROW, "&cClose", null));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;

        int slot = e.getSlot();
        List<String> reasons = reasons();

        if (slot >= REASON_START && slot < REASON_START + MAX_REASONS && slot - REASON_START < reasons.size()) {
            h.reason = slot - REASON_START;
            render(h);
            return;
        }
        if (slot == 53) {
            p.closeInventory();
            return;
        }
        if (slot == 49) {
            p.closeInventory();
            Bukkit.dispatchCommand(p, "history " + h.targetName);
            return;
        }
        for (Action a : actions) {
            if (a.slot() == slot) {
                apply(p, h, a, reasons);
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void apply(Player p, Holder h, Action a, List<String> reasons) {
        StaffManager sm = plugin.staff();
        PunishmentManager pm = plugin.punishments();

        if (!sm.has(p, a.perm())) {
            p.sendMessage(Util.component("&cYour rank can't use that punishment."));
            return;
        }
        if (!sm.canPunish(p, h.targetId)) {
            p.sendMessage(Util.component("&cYou can't punish someone of the same or higher rank."));
            return;
        }
        if (h.reason < 0 || h.reason >= reasons.size()) {
            p.sendMessage(Util.component("&cSelect a reason first."));
            return;
        }
        if (a.type() == Punishment.Type.BAN && pm.activeOf(h.targetId, Punishment.Type.BAN) != null) {
            p.sendMessage(Util.component("&cThat player is already banned."));
            return;
        }
        if (a.type() == Punishment.Type.MUTE && pm.activeOf(h.targetId, Punishment.Type.MUTE) != null) {
            p.sendMessage(Util.component("&cThat player is already muted."));
            return;
        }

        pm.punish(a.type(), h.targetId, h.targetName, p.getName(), reasons.get(h.reason), a.dur());
        p.closeInventory();
        p.sendMessage(Util.component("&aPunishment applied."));
    }
}
