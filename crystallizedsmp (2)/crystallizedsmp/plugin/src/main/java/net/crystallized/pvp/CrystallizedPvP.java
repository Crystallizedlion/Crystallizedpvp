package net.crystallized.pvp;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import net.crystallized.pvp.staff.PunishGUI;
import net.crystallized.pvp.staff.PunishmentManager;
import net.crystallized.pvp.staff.StaffCommands;
import net.crystallized.pvp.staff.StaffListener;
import net.crystallized.pvp.staff.StaffManager;
import org.bukkit.command.PluginCommand;

public class CrystallizedPvP extends JavaPlugin implements Listener, CommandExecutor {

    private File statsFile;
    private YamlConfiguration stats;
    private StaffManager staff;
    private PunishmentManager punishments;

    public StaffManager staff() {
        return staff;
    }

    public PunishmentManager punishments() {
        return punishments;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        statsFile = new File(getDataFolder(), "stats.yml");
        stats = YamlConfiguration.loadConfiguration(statsFile);

        getServer().getPluginManager().registerEvents(this, this);
        for (String cmd : new String[]{"kit", "spawn", "setspawn", "stats"}) {
            getCommand(cmd).setExecutor(this);
        }
        // ---- staff system
        staff = new StaffManager(this);
        punishments = new PunishmentManager(this);
        PunishGUI gui = new PunishGUI(this);
        StaffCommands staffCommands = new StaffCommands(this, gui);
        for (String c : new String[]{"staff", "ban", "tempban", "unban", "mute", "tempmute", "unmute",
                "kick", "warn", "history", "punish", "freeze", "vanish", "sc", "invsee",
                "clearchat", "mutechat", "report"}) {
            PluginCommand pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(staffCommands);
                pc.setTabCompleter(staffCommands);
            }
        }
        getServer().getPluginManager().registerEvents(new StaffListener(this), this);
        getServer().getPluginManager().registerEvents(gui, this);
        for (Player online : Bukkit.getOnlinePlayers()) staff.refreshDisplay(online);

        // autosave stats every 5 minutes
        getServer().getScheduler().runTaskTimer(this, this::saveStats, 6000L, 6000L);
        getLogger().info("crystallizedsmp Crystal PvP enabled.");
    }

    @Override
    public void onDisable() {
        saveStats();
        if (staff != null) staff.save();
        if (punishments != null) punishments.save();
    }

    // ---------------------------------------------------------------- helpers

    private net.kyori.adventure.text.Component color(String s) {
        return net.crystallized.pvp.staff.Util.component(s);
    }

    private void saveStats() {
        try {
            stats.save(statsFile);
        } catch (IOException e) {
            getLogger().warning("Could not save stats: " + e.getMessage());
        }
    }

    private void add(UUID id, String key, int n) {
        String path = id + "." + key;
        stats.set(path, stats.getInt(path) + n);
    }

    private Location spawn() {
        String w = getConfig().getString("spawn.world", "");
        World world = (w == null || w.isEmpty()) ? null : Bukkit.getWorld(w);
        if (world == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(world,
                getConfig().getDouble("spawn.x"),
                getConfig().getDouble("spawn.y"),
                getConfig().getDouble("spawn.z"),
                (float) getConfig().getDouble("spawn.yaw"),
                (float) getConfig().getDouble("spawn.pitch"));
    }

    private Enchantment enchant(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase()));
    }

    /** Parses "MATERIAL*AMOUNT:enchant=level,enchant=level". */
    private ItemStack parseItem(String s) {
        String[] parts = s.split(":", 2);
        String[] head = parts[0].split("\\*");
        Material m = Material.matchMaterial(head[0].trim());
        if (m == null) {
            getLogger().warning("Unknown material in kit: " + head[0]);
            return null;
        }
        int amount = head.length > 1 ? Integer.parseInt(head[1].trim()) : 1;
        ItemStack item = new ItemStack(m, amount);
        if (parts.length > 1) {
            for (String e : parts[1].split(",")) {
                String[] kv = e.split("=");
                Enchantment en = enchant(kv[0].trim());
                if (en == null) {
                    getLogger().warning("Unknown enchantment in kit: " + kv[0]);
                    continue;
                }
                item.addUnsafeEnchantment(en, Integer.parseInt(kv[1].trim()));
            }
        }
        return item;
    }

    private void equip(Player p, ItemStack it) {
        String n = it.getType().name();
        if (n.endsWith("_HELMET")) p.getInventory().setHelmet(it);
        else if (n.endsWith("_CHESTPLATE")) p.getInventory().setChestplate(it);
        else if (n.endsWith("_LEGGINGS")) p.getInventory().setLeggings(it);
        else if (n.endsWith("_BOOTS")) p.getInventory().setBoots(it);
        else p.getInventory().addItem(it);
    }

    private void heal(Player p) {
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    }

    private void giveKit(Player p) {
        p.getInventory().clear();
        for (String line : getConfig().getStringList("kit")) {
            ItemStack it = parseItem(line);
            if (it != null) equip(p, it);
        }
        String off = getConfig().getString("kit-offhand", "");
        if (off != null && !off.isEmpty()) {
            ItemStack it = parseItem(off);
            if (it != null) p.getInventory().setItemInOffHand(it);
        }
        heal(p);
    }

    private void applyNoCooldown(Player p) {
        if (!getConfig().getBoolean("pvp.no-attack-cooldown")) return;
        AttributeInstance inst = p.getAttribute(Attribute.ATTACK_SPEED);
        if (inst != null) inst.setBaseValue(100.0);
    }

    // ----------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        applyNoCooldown(p);
        p.teleport(spawn());
        giveKit(p);
        p.sendMessage(color(getConfig().getString("messages.join", "")));
    }

    /** Hitting a crystal pops it instantly, no client/server delay. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal crystal)) return;
        if (!getConfig().getBoolean("crystals.instant-break")) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player pl) attacker = pl;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2) attacker = pl2;
        if (attacker == null) return;

        e.setCancelled(true);
        Location loc = crystal.getLocation();
        crystal.remove();
        loc.getWorld().createExplosion(attacker, loc,
                (float) getConfig().getDouble("crystals.power", 6.0),
                false,
                getConfig().getBoolean("crystals.break-blocks"));
    }

    /** Keep the arena intact if configured. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (getConfig().getBoolean("crystals.break-blocks")) return;
        if (e.getEntity() instanceof EnderCrystal) e.blockList().clear();
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player
                && e.getCause() == EntityDamageEvent.DamageCause.FALL
                && getConfig().getBoolean("pvp.no-fall-damage")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (getConfig().getBoolean("pvp.no-hunger")) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepInventory(false);

        add(victim.getUniqueId(), "deaths", 1);
        stats.set(victim.getUniqueId() + ".streak", 0);

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            UUID k = killer.getUniqueId();
            add(k, "kills", 1);
            add(k, "streak", 1);
            int streak = stats.getInt(k + ".streak");
            if (streak > stats.getInt(k + ".best")) stats.set(k + ".best", streak);

            int every = getConfig().getInt("pvp.streak-announce-every", 5);
            if (every > 0 && streak % every == 0) {
                Bukkit.broadcast(color("&b" + killer.getName() + " &7is on a &f" + streak + " &7killstreak!"));
            }
            if (getConfig().getBoolean("pvp.refill-on-kill")) giveKit(killer);
        }

        if (getConfig().getBoolean("pvp.instant-respawn")) {
            Bukkit.getScheduler().runTask(this, () -> victim.spigot().respawn());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(spawn());
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            applyNoCooldown(p);
            giveKit(p);
        }, 1L);
    }

    // --------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        if (name.equals("stats")) {
            Player target = null;
            if (args.length > 0) target = Bukkit.getPlayerExact(args[0]);
            else if (sender instanceof Player p) target = p;
            if (target == null) {
                sender.sendMessage(color("&cPlayer not found."));
                return true;
            }
            String id = target.getUniqueId().toString();
            int kills = stats.getInt(id + ".kills");
            int deaths = stats.getInt(id + ".deaths");
            double kdr = deaths == 0 ? kills : Math.round((kills / (double) deaths) * 100.0) / 100.0;
            sender.sendMessage(color("&b" + target.getName() + " &7- Kills: &f" + kills
                    + " &7Deaths: &f" + deaths + " &7K/D: &f" + kdr
                    + " &7Streak: &f" + stats.getInt(id + ".streak")
                    + " &7Best: &f" + stats.getInt(id + ".best")));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        switch (name) {
            case "kit" -> {
                giveKit(p);
                p.sendMessage(color(getConfig().getString("messages.kit", "")));
            }
            case "spawn" -> p.teleport(spawn());
            case "setspawn" -> {
                if (!p.hasPermission("crystallized.admin")) {
                    p.sendMessage(color("&cNo permission."));
                    return true;
                }
                Location l = p.getLocation();
                getConfig().set("spawn.world", l.getWorld().getName());
                getConfig().set("spawn.x", l.getX());
                getConfig().set("spawn.y", l.getY());
                getConfig().set("spawn.z", l.getZ());
                getConfig().set("spawn.yaw", l.getYaw());
                getConfig().set("spawn.pitch", l.getPitch());
                saveConfig();
                p.sendMessage(color(getConfig().getString("messages.spawn-set", "")));
            }
            default -> { return false; }
        }
        return true;
    }
}
