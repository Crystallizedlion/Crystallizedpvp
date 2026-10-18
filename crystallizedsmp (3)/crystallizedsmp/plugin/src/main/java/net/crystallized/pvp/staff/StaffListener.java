package net.crystallized.pvp.staff;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.crystallized.pvp.CrystallizedPvP;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class StaffListener implements Listener {
    private final CrystallizedPvP plugin;
    private final StaffManager sm;
    private final PunishmentManager pm;

    public StaffListener(CrystallizedPvP plugin) {
        this.plugin = plugin;
        this.sm = plugin.staff();
        this.pm = plugin.punishments();
    }

    // ------------------------------------------------------------ login / join

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Punishment ban = pm.activeOf(e.getUniqueId(), Punishment.Type.BAN);
        if (ban != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Util.component(pm.banScreen(ban)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        sm.refreshDisplay(p);

        // hide vanished staff from normal players
        if (sm.level(p) < 1) {
            for (UUID id : sm.vanished) {
                Player v = Bukkit.getPlayer(id);
                if (v != null) p.hidePlayer(plugin, v);
            }
        }
        if (sm.level(p) >= 1) {
            sm.alert("&f" + p.getName() + " &7joined (" + sm.displayRank(p.getUniqueId()).name() + ")");
        }
        if (sm.frozen.contains(p.getUniqueId())) {
            p.sendMessage(Util.component("&cYou are still frozen. Wait for a staff member."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (sm.vanished.contains(p.getUniqueId())) e.quitMessage(null);
        if (sm.frozen.contains(p.getUniqueId())) {
            sm.alert("&c" + p.getName() + " &7logged out while frozen!");
        }
    }

    // -------------------------------------------------------------------- chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // staff chat toggle
        if (sm.staffChat.contains(id) && sm.level(p) >= 1) {
            e.setCancelled(true);
            sm.sendStaffChat(p.getName(), PlainTextComponentSerializer.plainText().serialize(e.message()));
            return;
        }

        Punishment mute = pm.activeOf(id, Punishment.Type.MUTE);
        if (mute != null) {
            e.setCancelled(true);
            p.sendMessage(Util.component(pm.muteMessage(mute)));
            return;
        }

        if (sm.chatMuted && sm.level(p) < 1) {
            e.setCancelled(true);
            p.sendMessage(Util.component("&cChat is currently muted."));
            return;
        }

        String prefix = sm.prefix(sm.displayRank(id));
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                Util.component(prefix + source.getName() + "&8» &f").append(message)));
    }

    // ------------------------------------------------------------------ freeze

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!sm.frozen.contains(e.getPlayer().getUniqueId())) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location l = from.clone();
            l.setYaw(to.getYaw());
            l.setPitch(to.getPitch());
            e.setTo(l);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (sm.frozen.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Util.component("&cYou can't use commands while frozen."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && sm.frozen.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent e) {
        Player damager = null;
        if (e.getDamager() instanceof Player p) damager = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2) damager = p2;
        if (damager != null && sm.frozen.contains(damager.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (sm.frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (sm.frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ invsee

    /** Only admins+ can edit someone else's inventory opened with /invsee. */
    @EventHandler
    public void onInvseeClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player clicker)) return;
        if (e.getView().getTopInventory().getHolder() instanceof Player owner
                && !owner.equals(clicker)
                && sm.level(clicker) < plugin.getConfig().getInt("staff.required.invsee-edit", 3)) {
            e.setCancelled(true);
        }
    }
}
