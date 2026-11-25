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
        Map<String, String> filled = new java.util.HashMap<String, String>();
        if (ench != null) {
            for (Map.Entry<String, Object> e : ench.getValues(false).entrySet()) {
                String key = e.getKey().toLowerCase(Locale.ROOT);
                int level = rollLevel(e.getValue());
                if (level <= 0) {
                    problems.add("'" + e.getValue() + "' isn't a valid level for " + e.getKey()
                            + " (use a number like 3, a range like \"1-2\", or chances like {1: 70, 2: 30})");
                    continue;
                }
                Enchants.Def d = c == null ? null : enchants.def(c, key);
                if (d != null) {
                    if (d.fits(item)) {
                        enchants.setLevel(item, d, level);
                        filled.put("{" + key + "}", number(d.effect(level)));
                        filled.put("{" + key + "-level}", enchants.levelText(level));
                    } else {
                        problems.add(d.name + " can't go on " + item.getType().name().toLowerCase(Locale.ROOT));
                    }
                    continue;
                }
                // not an Enchanter enchant: any normal Minecraft enchant works too (power, punch, infinity...)
                org.bukkit.enchantments.Enchantment vanilla = org.bukkit.enchantments.Enchantment.getByKey(
                        org.bukkit.NamespacedKey.minecraft(key.replace('-', '_')));
                if (vanilla != null) {
                    item.addUnsafeEnchantment(vanilla, level);
                    filled.put("{" + key + "-level}", enchants.levelText(level));
                } else {
                    problems.add("'" + e.getKey() + "' isn't an enchant in enchants.yml or a Minecraft enchant");
                }
            }
        }
        fillLore(item, filled);
        if (c != null) {
            enchants.redrawLore(item);
        }
        return item;
    }

    /**
     * A level from items.yml: a number (3), a range ("1-2", every level equally likely) or chances
     * per level ({1: 70, 2: 30}). Returns 0 when it can't be read.
     */
    static int rollLevel(Object value) {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        if (value instanceof ConfigurationSection || value instanceof Map) {
            Map<?, ?> map = value instanceof ConfigurationSection
                    ? ((ConfigurationSection) value).getValues(false) : (Map<?, ?>) value;
            List<int[]> options = new ArrayList<int[]>();
            int total = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                try {
                    int level = Integer.parseInt(String.valueOf(e.getKey()).trim());
                    int weight = (int) Math.round(Double.parseDouble(String.valueOf(e.getValue()).trim()));
                    if (level > 0 && weight > 0) {
                        options.add(new int[] {level, weight});
                        total += weight;
                    }
                } catch (NumberFormatException ignored) {
                    // skip a bad line
                }
            }
            if (total <= 0) {
                return 0;
            }
            int pick = rnd.nextInt(total);
            for (int[] o : options) {
                pick -= o[1];
                if (pick < 0) {
                    return o[0];
                }
            }
            return 0;
        }
        String text = String.valueOf(value).trim();
        try {
            int dash = text.indexOf('-');
            if (dash > 0) {
                int lo = Integer.parseInt(text.substring(0, dash).trim());
                int hi = Integer.parseInt(text.substring(dash + 1).trim());
                if (hi < lo) {
                    int t = lo;
                    lo = hi;
                    hi = t;
                }
                return lo + rnd.nextInt(hi - lo + 1);
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** 12.0 -> "12", 0.75 -> "0.75" */
    private static String number(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return new java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Puts the rolled numbers into the lore: {freeze} is Freeze's strength, {freeze-level} its level (III). */
    private static void fillLore(ItemStack item, Map<String, String> filled) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || filled.isEmpty()) {
            return;
        }
        List<String> lore = new ArrayList<String>();
        for (String line : meta.getLore()) {
            for (Map.Entry<String, String> f : filled.entrySet()) {
                line = line.replace(f.getKey(), f.getValue());
            }
            lore.add(line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
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
