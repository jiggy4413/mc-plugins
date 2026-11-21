package io.github.jiggy4413.prisoncore.tokens;

import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * PvP points: 1 per player kill, spent on sword and armor enchants.
 * Killing the same player again within a few minutes gives nothing, so friends can't farm each other.
 * Settings are in the "pvp-points:" part of tokens.yml; balances in data/pvp-points.yml.
 */
public final class PvpPoints implements Listener, CommandExecutor {

    private final JavaPlugin plugin;
    private final Tokens tokens;
    private final File dataFile;
    private final Map<UUID, Long> balances = new HashMap<UUID, Long>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    /** "killer:victim" → time of the last kill that paid out */
    private final Map<String, Long> lastKill = new HashMap<String, Long>();
    private boolean dirty;
    private BukkitTask saveTask;

    public PvpPoints(JavaPlugin plugin, Tokens tokens) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.dataFile = new File(new File(plugin.getDataFolder(), "data"), "pvp-points.yml");
    }

    private ConfigurationSection config() {
        ConfigurationSection s = tokens.config().getConfigurationSection("pvp-points");
        return s != null ? s : new YamlConfiguration();
    }

    public void loadBalances() {
        balances.clear();
        names.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection b = data.getConfigurationSection("balances");
        if (b == null) {
            return;
        }
        for (String key : b.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                balances.put(id, b.getLong(key));
                String name = data.getString("names." + key);
                if (name != null) {
                    names.put(id, name);
                }
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }

    public void start() {
        stop();
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    dirty = false;
                    final String text = snapshot();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                        @Override
                        public void run() {
                            write(text);
                        }
                    });
                }
            }
        }, 1200L, 1200L);
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    public void saveNow() {
        write(snapshot());
        dirty = false;
    }

    private String snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            data.set("balances." + e.getKey(), e.getValue());
            if (names.containsKey(e.getKey())) {
                data.set("names." + e.getKey(), names.get(e.getKey()));
            }
        }
        return data.saveToString();
    }

    private synchronized void write(String text) {
        try {
            dataFile.getParentFile().mkdirs();
            File tmp = new File(dataFile.getParentFile(), "pvp-points.yml.tmp");
            java.nio.file.Files.write(tmp.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't save PvP points: " + e);
        }
    }

    // ------------------------------------------------------------------ balances

    public long get(UUID id) {
        Long v = balances.get(id);
        return v == null ? 0 : v;
    }

    public void set(OfflinePlayer p, long amount) {
        balances.put(p.getUniqueId(), Math.max(0, amount));
        if (p.getName() != null) {
            names.put(p.getUniqueId(), p.getName());
        }
        dirty = true;
    }

    public boolean take(OfflinePlayer p, long amount) {
        long have = get(p.getUniqueId());
        if (have < amount) {
            return false;
        }
        set(p, have - amount);
        return true;
    }

    // ------------------------------------------------------------------ earning

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || !config().getBoolean("enabled", true)) {
            return;
        }
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = config().getLong("same-player-cooldown-seconds", 300) * 1000L;
        Long last = lastKill.get(key);
        if (last != null && now - last < cooldown) {
            String msg = config().getString("cooldown-message", "&7No PvP point: you killed &f{player} &7too recently.");
            if (!msg.isEmpty()) {
                killer.sendMessage(Text.color(killer, msg.replace("{player}", victim.getName())));
            }
            return;
        }
        lastKill.put(key, now);
        long amount = Math.max(0, config().getLong("per-kill", 1));
        set(killer, get(killer.getUniqueId()) + amount);
        String msg = config().getString("message", "&c+{amount} PvP Point &7for killing &f{player}");
        if (!msg.isEmpty()) {
            killer.sendMessage(Text.color(killer, msg.replace("{amount}", Tokens.format(amount))
                    .replace("{player}", victim.getName())));
        }
    }

    // ------------------------------------------------------------------ /pvppoints

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Usage: /pvppoints <player> | give|take|set <player> <amount>");
                return true;
            }
            sender.sendMessage(Text.color((Player) sender, "&7You have &c" + Tokens.format(get(((Player) sender).getUniqueId())) + " &7PvP points."));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
            if (!sender.hasPermission("prisoncore.tokens.admin")) {
                sender.sendMessage(Text.color("&cYou don't have permission to do that."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Text.color("&cUsage: /pvppoints " + sub + " <player> <amount>"));
                return true;
            }
            OfflinePlayer target = find(args[1]);
            if (target == null) {
                sender.sendMessage(Text.color("&c" + args[1] + " has never joined the server."));
                return true;
            }
            long amount;
            try {
                amount = Long.parseLong(args[2].replace(",", ""));
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (amount < 0) {
                sender.sendMessage(Text.color("&c" + args[2] + " isn't a valid amount."));
                return true;
            }
            long have = get(target.getUniqueId());
            set(target, sub.equals("give") ? have + amount : sub.equals("take") ? have - amount : amount);
            sender.sendMessage(Text.color("&7" + target.getName() + " now has &c" + Tokens.format(get(target.getUniqueId())) + " &7PvP points."));
            return true;
        }
        OfflinePlayer target = find(args[0]);
        if (target == null) {
            sender.sendMessage(Text.color("&c" + args[0] + " has never joined the server."));
            return true;
        }
        sender.sendMessage(Text.color("&f" + target.getName() + " &7has &c" + Tokens.format(get(target.getUniqueId())) + " &7PvP points."));
        return true;
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return Bukkit.getOfflinePlayer(e.getKey());
            }
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(name);
        return p.hasPlayedBefore() ? p : null;
    }
}
