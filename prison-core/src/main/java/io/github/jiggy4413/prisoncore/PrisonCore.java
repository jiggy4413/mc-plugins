package io.github.jiggy4413.prisoncore;

import io.github.jiggy4413.prisoncore.menu.MenuListener;
import io.github.jiggy4413.prisoncore.menu.RanksMenu;
import io.github.jiggy4413.prisoncore.mines.MineCommand;
import io.github.jiggy4413.prisoncore.mines.MineListener;
import io.github.jiggy4413.prisoncore.mines.MineManager;
import io.github.jiggy4413.prisoncore.mines.PapiHook;
import io.github.jiggy4413.prisoncore.util.LegacyCompassCleanup;
import io.github.jiggy4413.prisoncore.rankup.RankupBridge;
import io.github.jiggy4413.prisoncore.update.HotSwap;
import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.world.VoidGenerator;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrisonCore extends JavaPlugin {

    private static final String MENU_FILE = "ranks-menu.yml";

    private FileConfiguration menuConfig;
    /** Null when Rankup isn't installed or failed to hook. */
    private RankupBridge rankup;
    private HotSwap hotSwap;
    private MineManager mines;
    /** The PlaceholderAPI expansion, kept as Object so this class never needs PlaceholderAPI. */
    private Object placeholders;

    @Override
    public void onEnable() {
        // Load classes needed while unloading now, in case the jar is replaced while running.
        preload("io.github.jiggy4413.prisoncore.menu.RanksMenu",
                "io.github.jiggy4413.prisoncore.world.VoidGenerator",
                "io.github.jiggy4413.prisoncore.util.ItemTemplate",
                "io.github.jiggy4413.prisoncore.util.Text",
                "io.github.jiggy4413.prisoncore.mines.MineManager",
                "io.github.jiggy4413.prisoncore.mines.Mine",
                "io.github.jiggy4413.prisoncore.mines.ResetJob",
                "io.github.jiggy4413.prisoncore.mines.Composition");
        hotSwap = new HotSwap(this, getFile());

        loadMenuConfig();
        hookRankup();
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        mines = new MineManager(this);
        mines.load();
        getServer().getPluginManager().registerEvents(new MineListener(mines), this);
        MineCommand mineCommand = new MineCommand(mines, getDataFolder().getParentFile());
        getCommand("pmine").setExecutor(mineCommand);
        getCommand("pmine").setTabCompleter(mineCommand);
        mines.start();
        // the server menu compass (1.5.x) is shelved: remove leftover compasses
        LegacyCompassCleanup cleanup = new LegacyCompassCleanup(this);
        getServer().getPluginManager().registerEvents(cleanup, this);
        cleanup.cleanOnline();

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                placeholders = PapiHook.register(this, mines);
            } catch (Throwable t) {
                getLogger().warning("Couldn't add PlaceholderAPI placeholders: " + t);
            }
        }
        getLogger().info("PrisonCore " + getDescription().getVersion() + " enabled.");
    }

    /**
     * Lets Multiverse (or bukkit.yml) create empty worlds: /mv create <name> normal -g PrisonCore
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidGenerator();
    }

    /** Loads classes (and their anonymous inner classes) now, so a live jar swap can't break them. */
    private void preload(String... names) {
        ClassLoader cl = getClass().getClassLoader();
        for (String name : names) {
            try {
                Class.forName(name, false, cl);
                for (int i = 1; i <= 9; i++) {
                    try {
                        Class.forName(name + "$" + i, false, cl);
                    } catch (ClassNotFoundException e) {
                        break;
                    }
                }
            } catch (ClassNotFoundException e) {
                getLogger().warning("Couldn't preload " + name);
            }
        }
        // named inner classes
        try {
            Class.forName("io.github.jiggy4413.prisoncore.mines.MrlImporter$Result", false, cl);
        } catch (ClassNotFoundException ignored) {
            // not critical
        }
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            try {
                PapiHook.unregister(placeholders);
            } catch (Throwable ignored) {
                // PlaceholderAPI may already be gone during shutdown
            }
            placeholders = null;
        }
        if (mines != null) {
            mines.shutdown();   // finishes a half-done reset so no mine is left half full
        }
        // close any open menus so nobody keeps a stale one
        for (Player p : getServer().getOnlinePlayers()) {
            InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof RanksMenu) {
                p.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------ config

    /**
     * Reads ranks-menu.yml, creating it from the default the first time.
     * If the file has a mistake, the menu keeps its previous settings and this returns a
     * short description of the problem (null when everything loaded fine).
     */
    public String loadMenuConfig() {
        File file = new File(getDataFolder(), MENU_FILE);
        if (!file.exists()) {
            saveResource(MENU_FILE, false);
        }

        YamlConfiguration builtIn = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getResource(MENU_FILE), StandardCharsets.UTF_8));
        String problem = null;
        YamlConfiguration config = new YamlConfiguration();
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            problem = findTab(text);
            if (problem == null) {
                config.loadFromString(text);
            }
        } catch (InvalidConfigurationException e) {
            problem = describeYamlError(e.getMessage());
        } catch (IOException e) {
            problem = "Couldn't read " + MENU_FILE + ": " + e.getMessage();
        }

        if (problem != null) {
            getLogger().severe(MENU_FILE + " has a mistake: " + problem);
            if (menuConfig != null) {
                return problem; // keep the settings that were working
            }
            config = new YamlConfiguration(); // first start: use built-in settings only
        }
        // fall back to built-in defaults for anything missing from the file
        config.setDefaults(builtIn);
        menuConfig = config;
        return problem;
    }

    /** YAML doesn't allow tab characters for indentation, a very common mistake. */
    private static String findTab(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int hash = line.indexOf('#');
            String beforeComment = hash >= 0 ? line.substring(0, hash) : line;
            if (beforeComment.indexOf('\t') >= 0) {
                return "line " + (i + 1) + " has a Tab character. Use spaces to indent instead.";
            }
        }
        return null;
    }

    /** Keeps the useful part of SnakeYAML's long error, e.g. "line 12, column 5". */
    private static String describeYamlError(String message) {
        if (message == null) {
            return "invalid YAML";
        }
        Matcher m = Pattern.compile("line (\\d+), column (\\d+)").matcher(message);
        String where = m.find() ? " near line " + m.group(1) + ", column " + m.group(2) : "";
        String first = message.trim().split("\n")[0].trim();
        return "invalid YAML" + where + " (" + first + "). Check indentation and quotes.";
    }

    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }

    // ------------------------------------------------------------------ rankup

    private void hookRankup() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Rankup");
        if (plugin == null || !plugin.isEnabled()) {
            getLogger().warning("Rankup isn't installed or enabled, so the ranks menu is off.");
            rankup = null;
            return;
        }
        try {
            rankup = RankupBridge.create(plugin);
            getLogger().info("Hooked into Rankup " + plugin.getDescription().getVersion() + ".");
        } catch (Throwable t) {
            // e.g. a Rankup version whose code differs from 3.11.3
            rankup = null;
            getLogger().severe("Couldn't hook into Rankup: " + t);
        }
    }

    public RankupBridge getRankup() {
        return rankup;
    }

    public boolean isRanksMenuAvailable() {
        return rankup != null && rankup.ranksAvailable();
    }

    public void openRanksMenu(Player player, RanksMenu.Tab tab) {
        if (!isRanksMenuAvailable()) {
            player.sendMessage(Text.color(player, menuConfig.getString("messages.unavailable")));
            return;
        }
        try {
            new RanksMenu(this, player, tab).open();
        } catch (Throwable t) {
            player.sendMessage(Text.color(player, menuConfig.getString("messages.unavailable")));
            getLogger().severe("Error opening the ranks menu for " + player.getName() + ": " + t);
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("prisonranks")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can open the ranks menu.");
                return true;
            }
            boolean prestiges = args.length > 0 && args[0].toLowerCase().startsWith("p");
            openRanksMenu((Player) sender, prestiges ? RanksMenu.Tab.PRESTIGES : RanksMenu.Tab.RANKS);
            return true;
        }

        // /prisoncore
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("prisoncore.admin")) {
                sender.sendMessage(Text.color("&cYou don't have permission to do that."));
                return true;
            }
            // a newer PrisonCore jar was dropped in: swap to it live
            File update = hotSwap.findUpdate();
            if (update != null) {
                sender.sendMessage(Text.color("&eNew PrisonCore jar found (&f" + update.getName() + "&e), updating..."));
                String error = hotSwap.start(update, sender);
                if (error != null) {
                    sender.sendMessage(Text.color("&c" + error));
                }
                return true;
            }

            // otherwise just reload the config
            try {
                String problem = loadMenuConfig();
                hookRankup();
                String minesProblem = mines.load();
                if (minesProblem != null) {
                    sender.sendMessage(Text.color("&c" + minesProblem));
                }
                if (problem != null) {
                    // color codes off on purpose so the message shows exactly what's in the file
                    sender.sendMessage(Text.color("&cranks-menu.yml has a mistake, so nothing changed:"));
                    sender.sendMessage(ChatColor.GRAY + problem);
                } else {
                    sender.sendMessage(Text.color("&aPrisonCore reloaded &7(ranks-menu.yml, mines.yml). No new jar found."));
                }
            } catch (Exception e) {
                sender.sendMessage(Text.color("&cReload failed: " + e.getMessage() + " &7(check ranks-menu.yml for a YAML mistake)"));
                getLogger().severe("Reload failed: " + e);
            }
            return true;
        }

        sender.sendMessage(Text.color("<gradient:#FFB347:#FFD700>&lPrisonCore</gradient> &7v"
                + getDescription().getVersion()));
        sender.sendMessage(Text.color("&7Ranks menu: " + (isRanksMenuAvailable() ? "&aactive" : "&cinactive (Rankup not found)")));
        if (sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&e/prisoncore reload &7- reload ranks-menu.yml, or load a newer PrisonCore jar"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> options = command.getName().equalsIgnoreCase("prisonranks")
                ? Arrays.asList("ranks", "prestiges")
                : (sender.hasPermission("prisoncore.admin") ? Collections.singletonList("reload") : Collections.<String>emptyList());
        List<String> out = new ArrayList<String>();
        for (String o : options) {
            if (o.startsWith(args[0].toLowerCase())) {
                out.add(o);
            }
        }
        return out;
    }
}
