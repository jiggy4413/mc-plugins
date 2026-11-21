package io.github.jiggy4413.prisoncore.board;

import io.github.jiggy4413.prisoncore.PrisonCore;
import io.github.jiggy4413.prisoncore.rankup.Ladder;
import io.github.jiggy4413.prisoncore.rankup.LadderEntry;
import io.github.jiggy4413.prisoncore.rankup.RankupBridge;
import io.github.jiggy4413.prisoncore.tokens.Tokens;
import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.util.YamlFile;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * The sidebar on the right of the screen. Each player gets their own scoreboard; lines are
 * stored in team prefixes and only resent when they change, so nothing flickers.
 * Settings are in scoreboard.yml.
 */
public final class Board implements Listener, CommandExecutor {

    public static final String FILE = "scoreboard.yml";
    private static final int MAX_LINES = 15;
    private static final int TITLE_LIMIT = 128;
    private static final int LINE_LIMIT = 64;
    private static final Pattern HEX = Pattern.compile("§x(§[0-9a-fA-F]){6}");

    private final PrisonCore plugin;
    private final Tokens tokens;
    private final File hiddenFile;
    private FileConfiguration config;
    private BukkitTask task;
    private final Map<UUID, PlayerBoard> boards = new HashMap<UUID, PlayerBoard>();
    private final Set<UUID> hidden = new HashSet<UUID>();
    private final Map<UUID, CachedRank> ranks = new HashMap<UUID, CachedRank>();

    public Board(PrisonCore plugin, Tokens tokens) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.hiddenFile = new File(new File(plugin.getDataFolder(), "data"), "scoreboard-hidden.yml");
    }

    /** Loads scoreboard.yml. Returns a description of a mistake in it, or null. */
    public String load() {
        YamlFile f = YamlFile.load(plugin, FILE);
        if (f.problem != null) {
            plugin.getLogger().severe(FILE + " has a mistake: " + f.problem);
            if (config == null) {
                config = YamlFile.builtIn(plugin, FILE);
            }
            return FILE + " has a mistake, so the scoreboard didn't change: " + f.problem;
        }
        config = f.config;
        hidden.clear();
        if (hiddenFile.exists()) {
            for (String s : YamlConfiguration.loadConfiguration(hiddenFile).getStringList("hidden")) {
                try {
                    hidden.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
        return null;
    }

    public void start() {
        stop();
        // rebuild every board so title/line count changes from a reload show up
        for (Player p : Bukkit.getOnlinePlayers()) {
            remove(p);
            show(p);
        }
        long ticks = Math.max(5, config.getInt("update-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Gives everyone back the server's normal scoreboard (on disable). */
    public void shutdown() {
        stop();
        for (Player p : Bukkit.getOnlinePlayers()) {
            remove(p);
        }
        boards.clear();
    }

    private boolean enabledFor(Player p) {
        if (!config.getBoolean("enabled", true) || hidden.contains(p.getUniqueId())) {
            return false;
        }
        return !config.getStringList("disabled-worlds").contains(p.getWorld().getName());
    }

    private void show(Player p) {
        if (!enabledFor(p) || boards.containsKey(p.getUniqueId())) {
            return;
        }
        try {
            PlayerBoard b = new PlayerBoard(p);
            boards.put(p.getUniqueId(), b);
            b.update();
        } catch (Throwable t) {
            plugin.getLogger().warning("Couldn't show the scoreboard to " + p.getName() + ": " + t);
        }
    }

    private void remove(Player p) {
        PlayerBoard b = boards.remove(p.getUniqueId());
        if (b != null && p.isOnline() && p.getScoreboard() == b.scoreboard) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean want = enabledFor(p);
            PlayerBoard b = boards.get(p.getUniqueId());
            if (!want) {
                if (b != null) {
                    remove(p);
                }
                continue;
            }
            if (b == null) {
                show(p);
                continue;
            }
            try {
                b.update();
            } catch (Throwable t) {
                plugin.getLogger().warning("Couldn't update the scoreboard for " + p.getName() + ": " + t);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player p = event.getPlayer();
        // a tick later, so plugins that set a scoreboard on join don't overwrite ours
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    show(p);
                }
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
        ranks.remove(event.getPlayer().getUniqueId());
    }

    // ================================================================== /board

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players have a scoreboard.");
            return true;
        }
        Player p = (Player) sender;
        boolean nowHidden = !hidden.contains(p.getUniqueId());
        if (args.length > 0 && args[0].equalsIgnoreCase("on")) {
            nowHidden = false;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("off")) {
            nowHidden = true;
        }
        if (nowHidden) {
            hidden.add(p.getUniqueId());
            remove(p);
            p.sendMessage(Text.color(p, config.getString("messages.hidden", "&7Scoreboard hidden. &f/board &7to show it again.")));
        } else {
            hidden.remove(p.getUniqueId());
            show(p);
            p.sendMessage(Text.color(p, config.getString("messages.shown", "&7Scoreboard shown.")));
        }
        saveHidden();
        return true;
    }

    private void saveHidden() {
        YamlConfiguration data = new YamlConfiguration();
        List<String> list = new ArrayList<String>();
        for (UUID id : hidden) {
            list.add(id.toString());
        }
        data.set("hidden", list);
        try {
            hiddenFile.getParentFile().mkdirs();
            data.save(hiddenFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save " + hiddenFile.getName() + ": " + e);
        }
    }

    // ================================================================== one player's board

    private final class PlayerBoard {
        final Player player;
        final Scoreboard scoreboard;
        final Objective objective;
        final List<Team> teams = new ArrayList<Team>();
        final List<String> entries = new ArrayList<String>();
        final List<String> shown = new ArrayList<String>();
        String shownTitle;

        PlayerBoard(Player player) {
            this.player = player;
            this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            this.objective = scoreboard.registerNewObjective("prisoncore", "dummy", " ");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            int count = Math.min(MAX_LINES, config.getStringList("lines").size());
            ChatColor[] colors = ChatColor.values();
            for (int i = 0; i < count; i++) {
                // an invisible, unique entry per line; the visible text goes in the team prefix
                String entry = colors[i].toString() + ChatColor.RESET;
                Team team = scoreboard.registerNewTeam("line" + i);
                team.addEntry(entry);
                objective.getScore(entry).setScore(count - i);
                teams.add(team);
                entries.add(entry);
                shown.add(null);
            }
            player.setScoreboard(scoreboard);
        }

        void update() {
            if (player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard); // another plugin replaced it
            }
            String title = fit(Text.color(player, fill(config.getString("title", "&6&lPRISON"))), TITLE_LIMIT);
            if (!title.equals(shownTitle)) {
                objective.setDisplayName(title);
                shownTitle = title;
            }
            List<String> lines = config.getStringList("lines");
            for (int i = 0; i < teams.size(); i++) {
                String text = i < lines.size() ? fit(Text.color(player, fill(lines.get(i))), LINE_LIMIT) : "";
                if (!text.equals(shown.get(i))) {
                    teams.get(i).setPrefix(text);
                    shown.set(i, text);
                }
            }
        }

        private String fill(String line) {
            if (line.indexOf('{') < 0) {
                return line;
            }
            String out = line.replace("{player}", player.getName())
                    .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
            if (out.contains("{tokens")) {
                long t = tokens.get(player.getUniqueId());
                out = out.replace("{tokens}", Tokens.format(t)).replace("{tokens_short}", Tokens.formatShort(t));
            }
            if (out.contains("{balance")) {
                double bal = balance(player);
                out = out.replace("{balance}", new DecimalFormat("#,##0.##").format(bal))
                        .replace("{balance_short}", Tokens.formatShort((long) bal));
            }
            if (out.contains("{rank") || out.contains("{prestige")) {
                CachedRank r = rank(player);
                String prefix = out.contains("{rank}") || out.contains("{rank_prefix}") ? prefix(player) : "";
                boolean usePrefix = config.getString("rank-display", "prefix").equalsIgnoreCase("prefix") && !prefix.isEmpty();
                out = out.replace("{rank_prefix}", prefix.isEmpty() ? r.text : prefix)
                        .replace("{rank_name}", r.text)
                        .replace("{rank}", usePrefix ? prefix : r.text).replace("{prestige_name}", r.prestigeName)
                        .replace("{prestige}", String.valueOf(r.prestige));
            }
            return out;
        }
    }

    // ================================================================== values

    private static final class CachedRank {
        String text;
        int prestige;
        String prestigeName;
        long at;
    }

    /** The player's Rankup rank and prestige, refreshed at most every few seconds. */
    private CachedRank rank(Player p) {
        CachedRank c = ranks.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (c != null && now - c.at < 3000) {
            return c;
        }
        int prestige = 0;
        String prestigeName = config.getString("no-prestige", "None");
        String text = config.getString("no-rank", "None");
        RankupBridge rankup = plugin.getRankup();
        if (rankup != null && rankup.ranksAvailable()) {
            try {
                Ladder ladder = rankup.ranks(p);
                for (LadderEntry e : ladder.entries) {
                    if (e.state == LadderEntry.State.CURRENT) {
                        text = e.rankupDisplayName != null && !e.rankupDisplayName.isEmpty() ? e.rankupDisplayName : e.id;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // keep "None"
            }
        }
        if (rankup != null && rankup.prestigesAvailable()) {
            try {
                Ladder ladder = rankup.prestiges(p);
                if (ladder.current != null) {
                    prestige = ladder.current.position;
                    prestigeName = ladder.current.id;
                }
            } catch (Throwable ignored) {
                // not prestiged
            }
        }
        if (c == null) {
            c = new CachedRank();
            ranks.put(p.getUniqueId(), c);
        }
        c.text = text;
        c.prestige = prestige;
        c.prestigeName = prestigeName;
        c.at = now;
        return c;
    }

    private Object chat;
    private Method getPrefix;
    private boolean chatChecked;

    /** The player's chat prefix from Vault (LuckPerms), trimmed; "" if there isn't one. */
    private String prefix(Player p) {
        if (!chatChecked || chat == null) {
            chatChecked = true;
            try {
                Class<?> c = Class.forName("net.milkbowl.vault.chat.Chat");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(c);
                if (rsp != null) {
                    chat = rsp.getProvider();
                    getPrefix = c.getMethod("getPlayerPrefix", Player.class);
                }
            } catch (Throwable ignored) {
                chat = null;
            }
        }
        if (chat == null) {
            return "";
        }
        try {
            Object v = getPrefix.invoke(chat, p);
            return v == null ? "" : v.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private Object economy;
    private Method getBalance;
    private boolean economyChecked;

    /** The player's money from Vault's economy, without needing Vault at compile time. */
    private double balance(Player p) {
        if (!economyChecked || economy == null) {
            economyChecked = true;
            try {
                Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
                if (rsp != null) {
                    economy = rsp.getProvider();
                    getBalance = eco.getMethod("getBalance", org.bukkit.OfflinePlayer.class);
                }
            } catch (Throwable ignored) {
                economy = null;
            }
        }
        if (economy == null) {
            return 0;
        }
        try {
            return ((Number) getBalance.invoke(economy, p)).doubleValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    // ================================================================== length limits

    /**
     * Keeps text under the client's limit. Hex colors (14 characters each) are swapped for the
     * nearest of the 16 basic colors first, so long gradients still fit, then the text is cut.
     */
    static String fit(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        Matcher m = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group().replace("§x", "").replace("§", "");
            m.appendReplacement(sb, Matcher.quoteReplacement(nearest(Integer.parseInt(hex, 16)).toString()));
        }
        m.appendTail(sb);
        String out = collapse(sb.toString());
        if (out.length() > limit) {
            out = out.substring(0, limit);
            if (out.endsWith("§")) {
                out = out.substring(0, out.length() - 1);
            }
        }
        return out;
    }

    /** Drops a color code that's repeated right after the same color ("§6A§6B" → "§6AB"). */
    private static String collapse(String s) {
        StringBuilder out = new StringBuilder();
        char lastColor = 0;
        boolean formatSinceColor = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                boolean isColor = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
                if (isColor && code == lastColor && !formatSinceColor) {
                    i++;
                    continue;
                }
                if (isColor) {
                    lastColor = code;
                    formatSinceColor = false;
                } else if (code == 'r') {
                    lastColor = 0;
                } else {
                    formatSinceColor = true;
                }
                out.append(ch).append(s.charAt(i + 1));
                i++;
                continue;
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static final int[] RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private static ChatColor nearest(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < RGB.length; i++) {
            int dr = r - (RGB[i] >> 16 & 0xFF);
            int dg = g - (RGB[i] >> 8 & 0xFF);
            int db = b - (RGB[i] & 0xFF);
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return ChatColor.getByChar(Integer.toHexString(best).charAt(0));
    }
}
