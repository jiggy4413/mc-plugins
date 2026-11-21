package io.github.jiggy4413.prisoncore.tokens;

import io.github.jiggy4413.prisoncore.mines.Mine;
import io.github.jiggy4413.prisoncore.mines.MineManager;
import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.util.YamlFile;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tokens: a second currency, earned by mining and (later) spent on pickaxe enchants.
 * Balances live in memory and are saved to data/tokens.yml every minute and on shutdown.
 * Settings and messages are in tokens.yml.
 */
public final class Tokens implements Listener, CommandExecutor, TabCompleter {

    public static final String FILE = "tokens.yml";

    private final JavaPlugin plugin;
    private final MineManager mines;
    private final File dataFile;
    private FileConfiguration config;
    private final Map<UUID, Long> balances = new HashMap<UUID, Long>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    private boolean dirty;
    private BukkitTask saveTask;
    /** Extra token chance from the Token Finder enchant (1.0 = normal), set by the enchants. */
    private java.util.function.ToDoubleFunction<Player> chanceBoost;

    public Tokens(JavaPlugin plugin, MineManager mines) {
        this.plugin = plugin;
        this.mines = mines;
        this.dataFile = new File(new File(plugin.getDataFolder(), "data"), "tokens.yml");
    }

    // ================================================================== loading / saving

    /** Loads tokens.yml (settings). Returns a description of a mistake in it, or null. */
    public String load() {
        YamlFile f = YamlFile.load(plugin, FILE);
        if (f.problem != null) {
            plugin.getLogger().severe(FILE + " has a mistake: " + f.problem);
            if (config == null) {
                config = YamlFile.builtIn(plugin, FILE);
            }
            return FILE + " has a mistake, so token settings didn't change: " + f.problem;
        }
        config = f.config;
        return null;
    }

    /** Reads everyone's balances. Call once on enable. */
    public void loadBalances() {
        balances.clear();
        names.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        if (data.isConfigurationSection("balances")) {
            for (String key : data.getConfigurationSection("balances").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    balances.put(id, data.getLong("balances." + key));
                    String name = data.getString("names." + key);
                    if (name != null) {
                        names.put(id, name);
                    }
                } catch (IllegalArgumentException ignored) {
                    // not a UUID, skip
                }
            }
        }
    }

    public FileConfiguration config() {
        return config;
    }

    public void setChanceBoost(java.util.function.ToDoubleFunction<Player> boost) {
        this.chanceBoost = boost;
    }

    public void start() {
        stop();
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                saveAsync();
            }
        }, 1200L, 1200L);
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            data.set("balances." + e.getKey(), e.getValue());
            String name = names.get(e.getKey());
            if (name != null) {
                data.set("names." + e.getKey(), name);
            }
        }
        return data;
    }

    /** Saves in the background if anything changed (main thread takes the snapshot). */
    private void saveAsync() {
        if (!dirty) {
            return;
        }
        dirty = false;
        final String text = snapshot().saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                write(text);
            }
        });
    }

    /** Saves right now on this thread. Used on shutdown and live updates. */
    public void saveNow() {
        write(snapshot().saveToString());
        dirty = false;
    }

    private synchronized void write(String text) {
        try {
            dataFile.getParentFile().mkdirs();
            File tmp = new File(dataFile.getParentFile(), "tokens.yml.tmp");
            java.nio.file.Files.write(tmp.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't save token balances: " + e);
        }
    }

    // ================================================================== balances

    public long get(UUID id) {
        Long v = balances.get(id);
        return v == null ? 0 : v;
    }

    public void set(OfflinePlayer player, long amount) {
        balances.put(player.getUniqueId(), Math.max(0, amount));
        if (player.getName() != null) {
            names.put(player.getUniqueId(), player.getName());
        }
        dirty = true;
    }

    public void add(OfflinePlayer player, long amount) {
        set(player, get(player.getUniqueId()) + amount);
    }

    /** Takes tokens if the player has enough. Returns false (and changes nothing) otherwise. */
    public boolean take(OfflinePlayer player, long amount) {
        long have = get(player.getUniqueId());
        if (have < amount) {
            return false;
        }
        set(player, have - amount);
        return true;
    }

    /** 1234567 → "1,234,567" */
    public static String format(long amount) {
        return new DecimalFormat("#,###").format(amount);
    }

    /** 1234567 → "1.23M" */
    public static String formatShort(long amount) {
        String[] units = {"", "K", "M", "B", "T", "Q"};
        double v = amount;
        int i = 0;
        while (v >= 1000 && i < units.length - 1) {
            v /= 1000;
            i++;
        }
        if (i == 0) {
            return String.valueOf(amount);
        }
        String s = new DecimalFormat(v >= 100 ? "0" : v >= 10 ? "0.#" : "0.##").format(v);
        return s + units[i];
    }

    // ================================================================== earning

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.getBoolean("earning.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Mine mine = mines.mineAt(event.getBlock());
        if (mine == null && config.getBoolean("earning.only-in-mines", true)) {
            return;
        }
        double chance = config.getDouble("earning.chance-percent", 2.0);
        if (chanceBoost != null) {
            chance *= Math.max(0, chanceBoost.applyAsDouble(player));
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() * 100 >= chance) {
            return;
        }
        int min = Math.max(0, config.getInt("earning.min", 1));
        int max = Math.max(min, config.getInt("earning.max", 3));
        long amount = min + r.nextInt(max - min + 1);
        int number = mine == null ? 1 : mineNumber(mine.id());
        if (number > 1) {
            double bonus = config.getDouble("earning.bonus-percent-per-mine", 10) * (number - 1) / 100.0;
            amount = Math.round(amount * (1 + bonus));
        }
        if (amount <= 0) {
            return;
        }
        add(player, amount);
        notify(player, config.getString("earning.message", "&e+{amount} Tokens").replace("{amount}", format(amount)));
    }

    /** A → 1 ... Z → 26, for "amine"-style and single-letter mine names; 1 for anything else. */
    static int mineNumber(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        char c = s.charAt(0);
        boolean letter = c >= 'a' && c <= 'z';
        if (letter && (s.length() == 1 || s.equals(c + "mine"))) {
            return c - 'a' + 1;
        }
        return 1;
    }

    private void notify(Player player, String message) {
        String where = config.getString("earning.notify", "actionbar").toLowerCase(Locale.ROOT);
        if (where.equals("none") || message.isEmpty()) {
            return;
        }
        String text = Text.color(player, message);
        if (where.equals("chat")) {
            player.sendMessage(text);
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
        }
    }

    // ================================================================== /tokens

    private String msg(String key, String def) {
        return config.getString("messages." + key, def);
    }

    private void send(CommandSender to, String message) {
        if (message != null && !message.isEmpty()) {
            to.sendMessage(Text.color(to instanceof Player ? (Player) to : null, message));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Usage: /tokens <player> | give|take|set <player> <amount> | top");
                return true;
            }
            send(sender, msg("balance", "&7You have &e{amount} &7tokens.")
                    .replace("{amount}", format(get(((Player) sender).getUniqueId()))));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("pay")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can pay tokens.");
                return true;
            }
            if (args.length < 3) {
                send(sender, "&cUsage: /tokens pay <player> <amount>");
                return true;
            }
            Player from = (Player) sender;
            Player to = Bukkit.getPlayerExact(args[1]);
            if (to == null) {
                send(sender, msg("not-online", "&c{player} isn't online.").replace("{player}", args[1]));
                return true;
            }
            if (to.equals(from)) {
                send(sender, "&cYou can't pay yourself.");
                return true;
            }
            long amount = parse(sender, args[2]);
            if (amount <= 0) {
                return true;
            }
            if (!take(from, amount)) {
                send(sender, msg("not-enough", "&cYou don't have enough tokens."));
                return true;
            }
            add(to, amount);
            send(from, msg("paid", "&7You sent &e{amount} &7tokens to &f{player}&7.")
                    .replace("{amount}", format(amount)).replace("{player}", to.getName()));
            send(to, msg("received", "&f{player} &7sent you &e{amount} &7tokens.")
                    .replace("{amount}", format(amount)).replace("{player}", from.getName()));
            return true;
        }

        if (sub.equals("top")) {
            List<Map.Entry<UUID, Long>> list = new ArrayList<Map.Entry<UUID, Long>>(balances.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<UUID, Long>>() {
                @Override
                public int compare(Map.Entry<UUID, Long> a, Map.Entry<UUID, Long> b) {
                    return Long.compare(b.getValue(), a.getValue());
                }
            });
            send(sender, msg("top-header", "&6&lTop Tokens"));
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                Map.Entry<UUID, Long> e = list.get(i);
                String name = names.containsKey(e.getKey()) ? names.get(e.getKey()) : e.getKey().toString().substring(0, 8);
                send(sender, msg("top-line", "&e#{rank} &f{player} &7- &e{amount}")
                        .replace("{rank}", String.valueOf(i + 1)).replace("{player}", name)
                        .replace("{amount}", format(e.getValue())));
            }
            return true;
        }

        if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
            if (!sender.hasPermission("prisoncore.tokens.admin")) {
                send(sender, "&cYou don't have permission to do that.");
                return true;
            }
            if (args.length < 3) {
                send(sender, "&cUsage: /tokens " + sub + " <player> <amount>");
                return true;
            }
            OfflinePlayer target = find(args[1]);
            if (target == null) {
                send(sender, "&c" + args[1] + " has never joined the server.");
                return true;
            }
            long amount = parse(sender, args[2]);
            if (amount < 0 || (amount == 0 && !sub.equals("set"))) {
                return true;
            }
            if (sub.equals("give")) {
                add(target, amount);
                if (target.isOnline()) {
                    send(target.getPlayer(), msg("given", "&7You received &e{amount} &7tokens!")
                            .replace("{amount}", format(amount)));
                }
            } else if (sub.equals("take")) {
                set(target, get(target.getUniqueId()) - amount);
            } else {
                set(target, amount);
            }
            send(sender, "&7" + target.getName() + " now has &e" + format(get(target.getUniqueId())) + " &7tokens.");
            return true;
        }

        // /tokens <player>
        OfflinePlayer target = find(args[0]);
        if (target == null) {
            send(sender, "&c" + args[0] + " has never joined the server.");
            return true;
        }
        send(sender, msg("balance-other", "&f{player} &7has &e{amount} &7tokens.")
                .replace("{player}", target.getName() == null ? args[0] : target.getName())
                .replace("{amount}", format(get(target.getUniqueId()))));
        return true;
    }

    private long parse(CommandSender sender, String text) {
        try {
            long v = Long.parseLong(text.replace(",", ""));
            if (v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            send(sender, "&c" + text + " isn't a valid amount.");
            return -1;
        }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            options.add("pay");
            options.add("top");
            if (sender.hasPermission("prisoncore.tokens.admin")) {
                options.add("give");
                options.add("take");
                options.add("set");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
            for (String o : options) {
                if (o.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("top")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
