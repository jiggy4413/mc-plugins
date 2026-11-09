package io.github.jiggy4413.prisoncore;

import io.github.jiggy4413.prisoncore.menu.MenuListener;
import io.github.jiggy4413.prisoncore.menu.RanksMenu;
import io.github.jiggy4413.prisoncore.rankup.RankupBridge;
import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrisonCore extends JavaPlugin {

    private static final String MENU_FILE = "ranks-menu.yml";

    private FileConfiguration menuConfig;
    /** Null when Rankup isn't installed or failed to hook. */
    private RankupBridge rankup;

    @Override
    public void onEnable() {
        loadMenuConfig();
        hookRankup();
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getLogger().info("PrisonCore " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // close any open menus so nobody keeps a stale one
        for (Player p : getServer().getOnlinePlayers()) {
            InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof RanksMenu) {
                p.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------ config

    /** Reads ranks-menu.yml, creating it from the default the first time. */
    public void loadMenuConfig() {
        File file = new File(getDataFolder(), MENU_FILE);
        if (!file.exists()) {
            saveResource(MENU_FILE, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        // fall back to built-in defaults for anything missing from the file
        Reader defaults = new InputStreamReader(getResource(MENU_FILE), StandardCharsets.UTF_8);
        config.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        menuConfig = config;
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
            try {
                loadMenuConfig();
                hookRankup();
                sender.sendMessage(Text.color("&aPrisonCore reloaded &7(ranks-menu.yml)."));
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
            sender.sendMessage(Text.color("&e/prisoncore reload &7- reload ranks-menu.yml"));
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
