package io.github.jiggy4413.prisoncore.warps;

import io.github.jiggy4413.prisoncore.PrisonCore;
import io.github.jiggy4413.prisoncore.mines.Mine;
import io.github.jiggy4413.prisoncore.mines.MineManager;
import io.github.jiggy4413.prisoncore.rankup.Ladder;
import io.github.jiggy4413.prisoncore.rankup.LadderEntry;
import io.github.jiggy4413.prisoncore.rankup.RankupBridge;
import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.util.YamlFile;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The /warps menu: reads warps-menu.yml, finds the matching EssentialsX warps, decides which
 * warps a player has unlocked, and keeps open menus up to date.
 *
 * Teleporting is left to EssentialsX: clicking a warp runs "/warp <name>" as the player,
 * so Essentials' permissions, teleport delay and cooldowns all still apply.
 */
public final class Warps implements CommandExecutor {

    public static final String FILE = "warps-menu.yml";
    private static final Pattern LETTER_MINE = Pattern.compile("^([a-z])-?_?mine$");

    private final PrisonCore plugin;
    private final MineManager mines;
    private FileConfiguration config;
    private BukkitTask refreshTask;

    // Essentials warp cache, refreshed when the warps folder or config changes
    private Set<String> essentialsWarps;          // lower-case names; null = Essentials not installed
    private boolean perWarpPermission;
    private long cachedWarpsModified = Long.MIN_VALUE;
    private long cachedConfigModified = Long.MIN_VALUE;
    private int cachedWarpCount = -1;

    public Warps(PrisonCore plugin, MineManager mines) {
        this.plugin = plugin;
        this.mines = mines;
    }

    /** Loads warps-menu.yml. Returns a description of a mistake in it, or null. */
    public String load() {
        YamlFile f = YamlFile.load(plugin, FILE);
        if (f.problem != null) {
            plugin.getLogger().severe(FILE + " has a mistake: " + f.problem);
            if (config == null) {
                config = YamlFile.builtIn(plugin, FILE); // first start: built-in menu
            }
            return FILE + " has a mistake, so the warps menu didn't change: " + f.problem;
        }
        config = f.config;
        cachedWarpsModified = Long.MIN_VALUE; // re-read Essentials warps too
        return null;
    }

    public void start() {
        stop();
        long ticks = Math.max(10, Math.round(config.getDouble("refresh-seconds", 1) * 20));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                refreshOpenMenus();
            }
        }, ticks, ticks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /** Closes every open warps menu (on disable, so nobody keeps a dead one). */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof WarpsMenu) {
                p.closeInventory();
            }
        }
    }

    private void refreshOpenMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof WarpsMenu) {
                try {
                    ((WarpsMenu) holder).render();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Couldn't refresh the warps menu for " + p.getName() + ": " + t);
                }
            }
        }
    }

    public FileConfiguration config() {
        return config;
    }

    public MineManager mines() {
        return mines;
    }

    public PrisonCore plugin() {
        return plugin;
    }

    public void open(Player player) {
        open(player, "main");
    }

    public void open(Player player, String page) {
        try {
            new WarpsMenu(this, player, hasPage(page) ? page : "main").open();
        } catch (Throwable t) {
            player.sendMessage(Text.color("&cThe warps menu couldn't open. Tell staff to check the console."));
            plugin.getLogger().severe("Error opening the warps menu for " + player.getName() + ": " + t);
            t.printStackTrace();
        }
    }

    // ================================================================== pages

    /**
     * Files from 1.6.0-1.6.2 have one page at the top level ("warps:" with no "menus:").
     * Those still work as a single "main" page.
     */
    private boolean singlePage() {
        return !config.contains("menus", true) && config.contains("warps", true);
    }

    /** The settings for a page: title, rows, warps, buttons. */
    ConfigurationSection page(String id) {
        if (singlePage()) {
            return config;
        }
        ConfigurationSection p = config.getConfigurationSection("menus." + id);
        if (p == null) {
            p = config.getConfigurationSection("menus.main");
        }
        return p != null ? p : config;
    }

    boolean hasPage(String id) {
        if (id == null) {
            return false;
        }
        if (singlePage()) {
            return id.equals("main");
        }
        return config.getConfigurationSection("menus." + id) != null;
    }

    /** The page a back button goes to: the page's "back:" setting, or main for any other page. */
    String backPage(String id) {
        if (singlePage() || "main".equals(id)) {
            return null;
        }
        String back = page(id).getString("back", "main");
        return back == null || back.isEmpty() || back.equalsIgnoreCase("none") ? null : back;
    }

    List<String> pageIds() {
        List<String> out = new ArrayList<String>();
        if (singlePage()) {
            out.add("main");
            return out;
        }
        ConfigurationSection menus = config.getConfigurationSection("menus");
        if (menus != null) {
            out.addAll(menus.getKeys(false));
        }
        return out;
    }

    // ================================================================== warp entries

    /** One configured warp, with its Essentials warp name worked out. */
    static final class Entry {
        String key;
        ConfigurationSection section;
        int slot;
        /** Essentials warp to run "/warp" with; null for command entries. */
        String warp;
        /** Command to run instead of a warp, without the slash. */
        String command;
        /** Page of this menu to open instead of a warp. */
        String open;
        Mine mine;
        String rank;
        String permission;
        /** False when the Essentials warp couldn't be found (the entry is then hidden). */
        boolean exists;
    }

    List<Entry> entries(String pageId) {
        refreshEssentials();
        List<Entry> out = new ArrayList<Entry>();
        ConfigurationSection all = page(pageId).getConfigurationSection("warps");
        if (all == null) {
            return out;
        }
        for (String key : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Entry e = new Entry();
            e.key = key;
            e.section = s;
            e.slot = s.getInt("slot", -1);
            e.rank = s.getString("rank", "");
            e.permission = s.getString("permission", "");
            String mineId = s.getString("mine", key);
            e.mine = mineId == null || mineId.isEmpty() ? null : mines.get(mineId);
            if (e.mine == null && !s.contains("mine")) {
                // an entry named "l" (or "l-mine"...) shows the stats of the mine "lmine"
                String letter = letterOf(key);
                if (letter != null) {
                    e.mine = mines.get(letter + "mine");
                    mineId = letter + "mine";
                }
            }

            String command = s.getString("command", "");
            String open = s.getString("open", "");
            if (open != null && !open.trim().isEmpty()) {
                e.open = open.trim();
                e.mine = null;
                e.exists = true;
            } else if (command != null && !command.trim().isEmpty()) {
                command = command.trim();
                e.command = command.startsWith("/") ? command.substring(1) : command;
                e.exists = true;
            } else {
                e.warp = resolveWarp(s.getString("warp", key), mineId);
                e.exists = e.warp != null;
                if (e.warp == null) {
                    e.warp = s.getString("warp", key);
                }
            }
            out.add(e);
        }
        return out;
    }

    /**
     * The Essentials warp name to use. Tries the configured name first, then common ways of
     * naming a lettered mine's warp (a, amine, a-mine, mine_a...). Returns null when Essentials is
     * installed and has none of them; with Essentials missing, trusts the configured name.
     */
    private String resolveWarp(String configured, String mineId) {
        if (essentialsWarps == null) {
            return configured;
        }
        List<String> candidates = new ArrayList<String>();
        candidates.add(configured);
        String letter = letterOf(configured);
        if (letter == null && mineId != null) {
            letter = letterOf(mineId);
        }
        if (letter != null) {
            String[] forms = {letter, letter + "mine", letter + "-mine", letter + "_mine",
                    "mine" + letter, "mine-" + letter, "mine_" + letter};
            for (String f : forms) {
                candidates.add(f);
            }
        }
        for (String c : candidates) {
            if (c != null && essentialsWarps.contains(c.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }

    private static String letterOf(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.length() == 1 && Character.isLetter(n.charAt(0))) {
            return n;
        }
        Matcher m = LETTER_MINE.matcher(n);
        return m.matches() ? m.group(1) : null;
    }

    // ================================================================== locks

    /** Per-player data worked out once per menu draw. */
    final class Viewer {
        final Player player;
        private Ladder ranks;
        private boolean ranksLoaded;

        Viewer(Player player) {
            this.player = player;
        }

        /** True when the player can't use this warp yet. */
        boolean locked(Entry e) {
            String mode = config.getString("lock-by", "auto").toLowerCase(Locale.ROOT);
            if (mode.equals("none")) {
                return false;
            }
            String perm = permissionFor(e);
            if (mode.equals("permission")) {
                return perm != null && !player.hasPermission(perm);
            }
            if (mode.equals("rank")) {
                return rankLocked(e);
            }
            // auto: a permission when there is one, otherwise the rank
            if (perm != null) {
                return !player.hasPermission(perm);
            }
            return rankLocked(e);
        }

        private boolean rankLocked(Entry e) {
            if (e.rank == null || e.rank.isEmpty()) {
                return false;
            }
            LadderEntry rank = findRank(e.rank);
            if (rank == null) {
                return false; // unknown rank name: don't lock anyone out over a typo
            }
            return rank.state != LadderEntry.State.COMPLETED && rank.state != LadderEntry.State.CURRENT;
        }

        private LadderEntry findRank(String name) {
            if (!ranksLoaded) {
                ranksLoaded = true;
                RankupBridge rankup = plugin.getRankup();
                if (rankup != null && rankup.ranksAvailable()) {
                    try {
                        ranks = rankup.ranks(player);
                    } catch (Throwable t) {
                        ranks = null;
                    }
                }
            }
            return ranks == null ? null : matchRank(ranks, name);
        }
    }

    /** The permission that decides a lock: the entry's own, or Essentials' per-warp one. */
    String permissionFor(Entry e) {
        if (e.permission != null && !e.permission.isEmpty()) {
            return e.permission;
        }
        if (e.warp != null && essentialsWarps != null && perWarpPermission) {
            return "essentials.warps." + e.warp.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** Finds a rank by its Rankup name, display name, or the name plus "block" (e.g. "a" → "ablock"). */
    static LadderEntry matchRank(Ladder ladder, String name) {
        String n = name.trim();
        for (LadderEntry r : ladder.entries) {
            if (r.id.equalsIgnoreCase(n)) {
                return r;
            }
        }
        for (LadderEntry r : ladder.entries) {
            String display = r.rankupDisplayName == null ? "" : ChatColor.stripColor(Text.color(r.rankupDisplayName));
            if (display.trim().equalsIgnoreCase(n) || r.id.equalsIgnoreCase(n + "block")) {
                return r;
            }
        }
        return null;
    }

    // ================================================================== Essentials

    private void refreshEssentials() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null) {
            essentialsWarps = null;
            perWarpPermission = false;
            return;
        }
        File folder = new File(ess.getDataFolder(), "warps");
        File cfg = new File(ess.getDataFolder(), "config.yml");
        File[] files = folder.listFiles();
        int count = files == null ? 0 : files.length;
        if (folder.lastModified() == cachedWarpsModified && cfg.lastModified() == cachedConfigModified
                && count == cachedWarpCount && essentialsWarps != null) {
            return;
        }
        cachedWarpsModified = folder.lastModified();
        cachedConfigModified = cfg.lastModified();
        cachedWarpCount = count;

        Set<String> names = new LinkedHashSet<String>();
        if (files != null) {
            for (File f : files) {
                String fn = f.getName();
                if (!fn.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                    continue;
                }
                String name = fn.substring(0, fn.length() - 4);
                try {
                    String inFile = YamlConfiguration.loadConfiguration(f).getString("name");
                    if (inFile != null && !inFile.isEmpty()) {
                        name = inFile;
                    }
                } catch (Throwable ignored) {
                    // use the file name
                }
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }
        essentialsWarps = names;
        perWarpPermission = cfg.exists() && YamlConfiguration.loadConfiguration(cfg).getBoolean("per-warp-permission", false);
    }

    // ================================================================== /prisonwarps

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("prisoncore.admin")) {
                sender.sendMessage(Text.color("&cYou don't have permission to do that."));
                return true;
            }
            check(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can open the warps menu. Use /prisonwarps check to test the setup.");
            return true;
        }
        open((Player) sender, args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "main");
        return true;
    }

    /** Shows which Essentials warp and lock each menu entry uses, so setup mistakes are easy to spot. */
    private void check(CommandSender sender) {
        List<Entry> list = new ArrayList<Entry>();
        List<String> pageOf = new ArrayList<String>();
        for (String id : pageIds()) {
            for (Entry e : entries(id)) {
                list.add(e);
                pageOf.add(id);
            }
        }
        sender.sendMessage(Text.color("&6&lWarps menu check &7(" + list.size() + " entries on "
                + pageIds().size() + " page(s): " + String.join(", ", pageIds()) + ")"));
        if (essentialsWarps == null) {
            sender.sendMessage(Text.color("&cEssentials isn't installed, so warps can't be checked."));
        } else {
            sender.sendMessage(Text.color("&7Essentials per-warp-permission: "
                    + (perWarpPermission ? "&aon &7(locks use essentials.warps.<name>)" : "&eoff &7(locks use ranks)")));
        }
        Set<String> used = new LinkedHashSet<String>();
        Ladder ladder = null;
        RankupBridge rankup = plugin.getRankup();
        if (sender instanceof Player && rankup != null && rankup.ranksAvailable()) {
            try {
                ladder = rankup.ranks((Player) sender);
            } catch (Throwable ignored) {
                ladder = null;
            }
        }
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            StringBuilder line = new StringBuilder("&8[" + pageOf.get(i) + "] &f" + e.key + " &8(slot " + e.slot + ")&7: ");
            if (e.open != null) {
                line.append(hasPage(e.open) ? "opens page &a" + e.open : "&copens page '" + e.open + "', which doesn't exist");
            } else if (e.command != null) {
                line.append("runs &f/").append(e.command);
            } else if (e.exists) {
                line.append("warp &a").append(e.warp);
                used.add(e.warp.toLowerCase(Locale.ROOT));
            } else {
                line.append("&cno Essentials warp found &7(tried ").append(e.warp).append(", ...) - hidden");
            }
            String perm = permissionFor(e);
            if (perm != null) {
                line.append(" &7| perm &f").append(perm);
            } else if (e.rank != null && !e.rank.isEmpty()) {
                line.append(" &7| rank &f").append(e.rank);
                if (ladder != null && matchRank(ladder, e.rank) == null) {
                    line.append(" &c(no Rankup rank with that name)");
                }
            }
            if (e.section.contains("mine") && e.mine == null) {
                line.append(" &c| no PrisonCore mine '").append(e.section.getString("mine")).append("'");
            }
            sender.sendMessage(Text.color(line.toString()));
        }
        if (essentialsWarps != null) {
            List<String> unused = new ArrayList<String>();
            for (String w : essentialsWarps) {
                if (!used.contains(w)) {
                    unused.add(w);
                }
            }
            if (!unused.isEmpty()) {
                sender.sendMessage(Text.color("&7Essentials warps not in the menu: &f" + String.join(", ", unused)));
            }
        }
    }
}
