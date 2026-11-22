package io.github.jiggy4413.prisoncore.enchants;

import io.github.jiggy4413.prisoncore.util.ItemTemplate;
import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.util.YamlFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Special items (crate prizes, event rewards...) defined in items.yml and handed out with
 * /prisoncore give <player> <item>. Each item is built fresh with real enchants, so crate plugins
 * only have to run the command instead of storing the item.
 */
public final class SpecialItems {

    public static final String FILE = "items.yml";

    private final JavaPlugin plugin;
    private final Enchants enchants;
    private FileConfiguration config;

    public SpecialItems(JavaPlugin plugin, Enchants enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    public String load() {
        YamlFile f = YamlFile.load(plugin, FILE);
        if (f.problem != null) {
            plugin.getLogger().severe(FILE + " has a mistake: " + f.problem);
            if (config == null) {
                config = YamlFile.builtIn(plugin, FILE);
            }
            return FILE + " has a mistake, so special items didn't change: " + f.problem;
        }
        config = f.config;
        return null;
    }

    public List<String> names() {
        ConfigurationSection items = config.getConfigurationSection("items");
        return items == null ? new ArrayList<String>() : new ArrayList<String>(items.getKeys(false));
    }

    /** Builds an item for a player. Problems (unknown enchants...) are added to {@code problems}. */
    public ItemStack build(String name, Player viewer, List<String> problems) {
        ConfigurationSection s = config.getConfigurationSection("items." + name);
        if (s == null) {
            for (String n : names()) {
                if (n.equalsIgnoreCase(name)) {
                    s = config.getConfigurationSection("items." + n);
                }
            }
        }
        if (s == null) {
            return null;
        }
        ItemStack item = ItemTemplate.of(s).build(viewer, null, null);
        if (s.isString("texture")) {
            ItemTemplate.applyTexture(item, s.getString("texture"));
        }
        ConfigurationSection ench = s.getConfigurationSection("enchants");
        Enchants.Category c = Enchants.category(item);
        if (ench != null) {
            if (c == null) {
                problems.add(item.getType() + " can't have Enchanter enchants (only pickaxes, swords and armor)");
            } else {
                for (Map.Entry<String, Object> e : ench.getValues(false).entrySet()) {
                    Enchants.Def d = enchants.def(c, e.getKey().toLowerCase(Locale.ROOT));
                    int level;
                    try {
                        level = Integer.parseInt(String.valueOf(e.getValue()).trim());
                    } catch (NumberFormatException ex) {
                        level = 0;
                    }
                    if (d == null) {
                        problems.add("'" + e.getKey() + "' isn't a " + c.name().toLowerCase(Locale.ROOT) + " enchant in enchants.yml");
                    } else if (level <= 0) {
                        problems.add("'" + e.getValue() + "' isn't a valid level for " + e.getKey());
                    } else if (!d.fits(item)) {
                        problems.add(d.name + " can't go on " + item.getType().name().toLowerCase(Locale.ROOT));
                    } else {
                        enchants.setLevel(item, d, level);
                    }
                }
            }
        }
        if (c != null) {
            enchants.redrawLore(item);
        }
        return item;
    }

    /** /prisoncore give <player> <item> [amount] */
    public void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission to do that."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.color("&cUsage: /prisoncore give <player> <item> [amount]"));
            sender.sendMessage(Text.color("&7Items: &f" + String.join(", ", names())));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.color("&c" + args[1] + " isn't online."));
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        List<String> problems = new ArrayList<String>();
        ItemStack item = build(args[2], target, problems);
        if (item == null) {
            sender.sendMessage(Text.color("&cThere's no item called '" + args[2] + "' in items.yml. Items: &f"
                    + String.join(", ", names())));
            return;
        }
        for (String p : problems) {
            sender.sendMessage(Text.color("&e" + FILE + " (" + args[2] + "): " + p));
            plugin.getLogger().warning(FILE + " (" + args[2] + "): " + p);
        }
        item.setAmount(amount);
        Map<Integer, ItemStack> left = target.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest); // full inventory: drop it at their feet
        }
        if (!(sender instanceof Player) || !sender.equals(target)) {
            sender.sendMessage(Text.color("&7Gave &f" + target.getName() + " &7" + args[2] + (amount > 1 ? " x" + amount : "") + "."));
        } else {
            sender.sendMessage(Text.color("&7Gave you " + args[2] + "."));
        }
    }
}
