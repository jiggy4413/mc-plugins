package io.github.jiggy4413.prisoncore.enchants;

import io.github.jiggy4413.prisoncore.mines.Mine;
import io.github.jiggy4413.prisoncore.mines.MineManager;
import io.github.jiggy4413.prisoncore.tokens.PvpPoints;
import io.github.jiggy4413.prisoncore.tokens.Tokens;
import io.github.jiggy4413.prisoncore.util.Text;
import io.github.jiggy4413.prisoncore.util.YamlFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Custom enchanting: a menu (replacing the enchanting table) where players spend tokens on
 * pickaxe enchants and PvP points on sword and armor enchants.
 *
 * Vanilla enchants (Efficiency, Sharpness, Protection...) are real enchants and can go past their
 * normal max. Prison enchants (Fortune, Token Finder, Explosive, Haste, Lifesteal) are stored on
 * the item and handled here. All enchants are listed in the item's lore; settings are in enchants.yml.
 */
public final class Enchants implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {

    public static final String FILE = "enchants.yml";

    public enum Category {
        PICKAXE, SWORD, ARMOR;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One enchant from enchants.yml. */
    static final class Def {
        String id;
        Category category;
        String name;
        Enchantment vanilla;     // null for prison enchants
        int max;
        double baseCost;
        double growth;
        /** Exact price of each level (level 1 first). When set, it replaces cost/cost-growth. */
        List<Long> costs = new ArrayList<Long>();
        double perLevel;
        /** Exact strength at each level (level 1 first). When set, it replaces per-level. */
        List<Double> strengths = new ArrayList<Double>();

        /** How strong the enchant is at a level: from "strength:" if set, else level x per-level. */
        double effect(int level) {
            if (level <= 0) {
                return 0;
            }
            if (!strengths.isEmpty()) {
                return strengths.get(Math.min(level, strengths.size()) - 1);
            }
            return level * perLevel;
        }
        Material icon;
        List<String> description = new ArrayList<String>();
        List<String> appliesTo = new ArrayList<String>();
        /** False for crate-only enchants: not sold in the Enchanter, only added with /enchanter apply. */
        boolean inMenu = true;
        /** Where it sits on its Enchanter page, or -1 to fill the next free spot. */
        int slot = -1;   // e.g. BOOTS; empty = every item of the category

        long cost(int currentLevel) {
            if (!costs.isEmpty()) {
                return costs.get(Math.min(currentLevel, costs.size() - 1));
            }
            return Math.max(1, Math.round(baseCost * Math.pow(growth, currentLevel)));
        }

        boolean fits(ItemStack item) {
            if (appliesTo.isEmpty()) {
                return true;
            }
            String type = item.getType().name();
            for (String a : appliesTo) {
                if (type.endsWith(a.toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    private final JavaPlugin plugin;
    private final MineManager mines;
    private final Tokens tokens;
    private final PvpPoints pvp;
    private final NamespacedKey loreKey;
    private FileConfiguration config;
    private final Map<Category, LinkedHashMap<String, Def>> defs = new HashMap<Category, LinkedHashMap<String, Def>>();
    private BukkitTask hasteTask;

    public Enchants(JavaPlugin plugin, MineManager mines, Tokens tokens, PvpPoints pvp) {
        this.plugin = plugin;
        this.mines = mines;
        this.tokens = tokens;
        this.pvp = pvp;
        this.loreKey = new NamespacedKey(plugin, "enchant_lore_lines");
    }

    // ================================================================== config

    public String load() {
        YamlFile f = YamlFile.load(plugin, FILE);
        if (f.problem != null) {
            plugin.getLogger().severe(FILE + " has a mistake: " + f.problem);
            if (config == null) {
                config = YamlFile.builtIn(plugin, FILE);
                readDefs();
            }
            return FILE + " has a mistake, so enchants didn't change: " + f.problem;
        }
        config = f.config;
        readDefs();
        return null;
    }

    private void readDefs() {
        defs.clear();
        for (Category c : Category.values()) {
            LinkedHashMap<String, Def> map = new LinkedHashMap<String, Def>();
            ConfigurationSection section = config.getConfigurationSection("enchants." + c.key());
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(id);
                    if (s == null || !s.getBoolean("enabled", true)) {
                        continue;
                    }
                    Def d = new Def();
                    d.id = id.toLowerCase(Locale.ROOT);
                    d.category = c;
                    d.name = s.getString("name", id);
                    String vanilla = s.getString("vanilla", "");
                    if (vanilla != null && !vanilla.isEmpty()) {
                        d.vanilla = Enchantment.getByKey(NamespacedKey.minecraft(vanilla.toLowerCase(Locale.ROOT)));
                        if (d.vanilla == null) {
                            plugin.getLogger().warning(FILE + ": unknown vanilla enchant '" + vanilla + "' for " + id);
                            continue;
                        }
                    }
                    d.max = Math.max(1, s.getInt("max-level", 1));
                    d.baseCost = s.getDouble("cost", 10);
                    d.growth = Math.max(1, s.getDouble("cost-growth", 1.2));
                    d.perLevel = s.getDouble("per-level", 0);
                    if (s.contains("strength", true) && s.isList("strength")) {
                        for (Object o : s.getList("strength")) {
                            try {
                                d.strengths.add(Double.parseDouble(o.toString().trim()));
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning(FILE + ": '" + o + "' in " + c.key() + "." + id + ".strength isn't a number");
                            }
                        }
                    }
                    if (s.contains("costs", true) && s.isList("costs")) {
                        for (Object o : s.getList("costs")) {
                            try {
                                d.costs.add(Math.max(0, Long.parseLong(o.toString().replace(",", "").replace("_", "").trim())));
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning(FILE + ": '" + o + "' in " + c.key() + "." + id + ".costs isn't a number");
                            }
                        }
                        if (!d.costs.isEmpty()) {
                            d.max = d.costs.size(); // one price per level
                        }
                    }
                    d.inMenu = s.getBoolean("in-menu", true);
                    d.slot = s.getInt("slot", -1);
                    if (d.costs.isEmpty() && !d.strengths.isEmpty() && !s.contains("max-level", true)) {
                        d.max = d.strengths.size(); // crate-only enchants: one strength per level
                    }
                    Material icon = Material.matchMaterial(s.getString("icon", "ENCHANTED_BOOK"));
                    d.icon = icon != null && icon.isItem() ? icon : Material.ENCHANTED_BOOK;
                    d.description = s.getStringList("description");
                    d.appliesTo = s.getStringList("only-for");
                    map.put(d.id, d);
                }
            }
            defs.put(c, map);
        }
    }

    FileConfiguration config() {
        return config;
    }

    Collection<Def> defs(Category c) {
        LinkedHashMap<String, Def> m = defs.get(c);
        return m == null ? new ArrayList<Def>() : m.values();
    }

    Tokens tokens() {
        return tokens;
    }

    PvpPoints pvp() {
        return pvp;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    public void start() {
        stop();
        tokens.setChanceBoost(new java.util.function.ToDoubleFunction<Player>() {
            @Override
            public double applyAsDouble(Player p) {
                Def d = def(Category.PICKAXE, "token-finder");
                ItemStack tool = p.getInventory().getItemInMainHand();
                if (d == null || category(tool) != Category.PICKAXE) {
                    return 1;
                }
                return 1 + d.effect(level(tool, d)) / 100.0;
            }
        });
        hasteTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                applyHaste();
            }
        }, 40L, 40L);
    }

    public void stop() {
        if (hasteTask != null) {
            hasteTask.cancel();
            hasteTask = null;
        }
        tokens.setChanceBoost(null);
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof EnchantMenu) {
                p.closeInventory();
            }
        }
    }

    Def def(Category c, String id) {
        LinkedHashMap<String, Def> m = defs.get(c);
        return m == null ? null : m.get(id);
    }

    // ================================================================== items

    /** Which upgrade menu an item belongs to, or null. */
    public static Category category(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        String t = item.getType().name();
        if (t.endsWith("_PICKAXE")) {
            return Category.PICKAXE;
        }
        if (t.endsWith("_SWORD")) {
            return Category.SWORD;
        }
        if (t.endsWith("_HELMET") || t.endsWith("_CHESTPLATE") || t.endsWith("_LEGGINGS") || t.endsWith("_BOOTS")) {
            return Category.ARMOR;
        }
        return null;
    }

    private NamespacedKey key(Def d) {
        return new NamespacedKey(plugin, "ench_" + d.id.replace('-', '_'));
    }

    int level(ItemStack item, Def d) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        if (d.vanilla != null) {
            return item.getEnchantmentLevel(d.vanilla);
        }
        ItemMeta meta = item.getItemMeta();
        Integer v = meta.getPersistentDataContainer().get(key(d), PersistentDataType.INTEGER);
        if (v != null) {
            return v;
        }
        // Items saved by other plugins (e.g. crate rewards) can lose the hidden data but keep their
        // lore, so read the level from the enchant's lore line instead.
        return loreLevel(meta, d);
    }

    /** The level shown in a lore line like "Explosive II" or "Explosive 2", or 0. */
    private int loreLevel(ItemMeta meta, Def d) {
        if (!meta.hasLore() || meta.getPersistentDataContainer().has(loreKey, PersistentDataType.INTEGER)) {
            return 0; // items we drew ourselves keep their levels in the hidden data
        }
        String prefix = d.name.toLowerCase(Locale.ROOT) + " ";
        for (String line : meta.getLore()) {
            String plain = org.bukkit.ChatColor.stripColor(line).trim();
            if (plain.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                int lvl = parseLevel(plain.substring(prefix.length()).trim());
                if (lvl > 0) {
                    return lvl;
                }
            }
        }
        return 0;
    }

    /** "IV" or "4" → 4; anything else → 0. */
    static int parseLevel(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            // try Roman numerals
        }
        String r = text.toUpperCase(Locale.ROOT);
        if (r.isEmpty() || !r.matches("[IVXLCDM]+")) {
            return 0;
        }
        int total = 0;
        int prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            char ch = r.charAt(i);
            int v = ch == 'I' ? 1 : ch == 'V' ? 5 : ch == 'X' ? 10 : ch == 'L' ? 50 : ch == 'C' ? 100 : ch == 'D' ? 500 : 1000;
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return roman(total).equals(r) ? total : 0;
    }

    /**
     * Turns lore-only enchants (from a crate reward that lost its hidden data) back into real ones,
     * so upgrading or redrawing the item keeps them. Returns true if anything changed.
     */
    boolean adoptLore(ItemStack item) {
        Category c = category(item);
        if (c == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore() || meta.getPersistentDataContainer().has(loreKey, PersistentDataType.INTEGER)) {
            return false;
        }
        boolean changed = false;
        List<String> lore = new ArrayList<String>(meta.getLore());
        for (Def d : defs(c)) {
            if (d.vanilla != null) {
                continue;
            }
            int lvl = loreLevel(meta, d);
            if (lvl > 0) {
                meta.getPersistentDataContainer().set(key(d), PersistentDataType.INTEGER, lvl);
                String prefix = d.name.toLowerCase(Locale.ROOT) + " ";
                for (java.util.Iterator<String> it = lore.iterator(); it.hasNext(); ) {
                    if (org.bukkit.ChatColor.stripColor(it.next()).trim().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        it.remove();
                    }
                }
                changed = true;
            }
        }
        if (changed) {
            // also drop lines for normal enchants; redrawLore writes the full list again
            for (Def d : defs(c)) {
                if (d.vanilla == null) {
                    continue;
                }
                String prefix = d.name.toLowerCase(Locale.ROOT) + " ";
                for (java.util.Iterator<String> it = lore.iterator(); it.hasNext(); ) {
                    if (org.bukkit.ChatColor.stripColor(it.next()).trim().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        it.remove();
                    }
                }
            }
            meta.setLore(lore.isEmpty() ? null : lore);
            meta.getPersistentDataContainer().set(loreKey, PersistentDataType.INTEGER, 0);
            item.setItemMeta(meta);
            redrawLore(item);
        }
        return changed;
    }

    /** Sets an enchant's level on the item and redraws the enchant lines in its lore. */
    void setLevel(ItemStack item, Def d, int level) {
        adoptLore(item);
        if (d.vanilla != null) {
            if (level <= 0) {
                item.removeEnchantment(d.vanilla);
            } else {
                item.addUnsafeEnchantment(d.vanilla, level);
            }
        } else {
            ItemMeta meta = item.getItemMeta();
            if (level <= 0) {
                meta.getPersistentDataContainer().remove(key(d));
            } else {
                meta.getPersistentDataContainer().set(key(d), PersistentDataType.INTEGER, level);
            }
            item.setItemMeta(meta);
        }
        redrawLore(item);
    }

    /** Puts one lore line per enchant at the top of the lore, keeping any other lore below it. */
    void redrawLore(ItemStack item) {
        Category c = category(item);
        if (c == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        Integer owned = meta.getPersistentDataContainer().get(loreKey, PersistentDataType.INTEGER);
        for (int i = 0; owned != null && i < owned && !lore.isEmpty(); i++) {
            lore.remove(0);
        }
        List<String> ours = new ArrayList<String>();
        String format = config.getString("lore-format", "&7{name} &f{level}");
        for (Def d : defs(c)) {
            int lvl = d.vanilla != null ? item.getEnchantmentLevel(d.vanilla) : levelFromMeta(meta, d);
            if (lvl > 0) {
                ours.add(Text.color(format.replace("{name}", d.name).replace("{level}", levelText(lvl))
                        .replace("{number}", String.valueOf(lvl)).replace("{roman}", roman(lvl))));
            }
        }
        lore.addAll(0, ours);
        meta.setLore(lore.isEmpty() ? null : lore);
        meta.getPersistentDataContainer().set(loreKey, PersistentDataType.INTEGER, ours.size());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    /** A level as players see it: Roman numerals like vanilla (III), unless roman-numerals is off. */
    String levelText(int level) {
        return config.getBoolean("roman-numerals", true) ? roman(level) : String.valueOf(level);
    }

    /** 1 → I, 4 → IV, 10 → X ... (plain number above 3999). */
    static String roman(int n) {
        if (n <= 0 || n >= 4000) {
            return String.valueOf(n);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                n -= values[i];
                sb.append(symbols[i]);
            }
        }
        return sb.toString();
    }

    private int levelFromMeta(ItemMeta meta, Def d) {
        Integer v = meta.getPersistentDataContainer().get(key(d), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    // ================================================================== effects

    int heldLevel(Player p, Category c, String id) {
        Def d = def(c, id);
        ItemStack tool = p.getInventory().getItemInMainHand();
        return d == null || category(tool) != c ? 0 : level(tool, d);
    }

    private void applyHaste() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int lvl = heldLevel(p, Category.PICKAXE, "haste");
            if (lvl > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 80, lvl - 1, true, false, false), true);
            }
        }
    }

    /** Fortune: more of every block mined in a mine, straight into the inventory. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Player p = event.getPlayer();
        int lvl = heldLevel(p, Category.PICKAXE, "fortune");
        if (lvl <= 0) {
            return;
        }
        Block b = event.getBlock();
        if (config.getBoolean("fortune-only-in-mines", true)
                && mines.mineAt(b) == null) {
            return;
        }
        Def d = def(Category.PICKAXE, "fortune");
        if (d.vanilla != null) {
            return; // Minecraft's own Fortune handles the drops
        }
        double multiplier = 1 + d.effect(lvl) / 100.0;
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (Item it : event.getItems()) {
            ItemStack stack = it.getItemStack().clone();
            stack.setAmount(randomRound(stack.getAmount() * multiplier));
            items.add(stack);
        }
        event.getItems().clear();
        give(p, items, b);
    }

    /** Explosive: sometimes breaks the blocks around the mined one (only inside the same mine). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        int lvl = heldLevel(p, Category.PICKAXE, "explosive");
        if (lvl <= 0) {
            return;
        }
        Def d = def(Category.PICKAXE, "explosive");
        if (ThreadLocalRandom.current().nextDouble() * 100 >= d.effect(lvl)) {
            return;
        }
        Block center = event.getBlock();
        Mine m = mines.mineAt(center);
        if (m == null || m.isResetting()) {
            return;
        }
        int r = Math.max(1, Math.min(3, config.getInt("explosive-radius", 1)));
        ItemStack tool = p.getInventory().getItemInMainHand();
        int fortune = heldLevel(p, Category.PICKAXE, "fortune");
        Def fortuneDef = def(Category.PICKAXE, "fortune");
        // with Minecraft's Fortune, getDrops(tool) below already applies it
        double multiplier = fortune > 0 && fortuneDef != null && fortuneDef.vanilla == null
                ? 1 + fortuneDef.effect(fortune) / 100.0 : 1;
        List<ItemStack> items = new ArrayList<ItemStack>();
        int broken = 0;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    Block b = center.getRelative(x, y, z);
                    if (b.getType().isAir() || b.getType() == Material.BEDROCK
                            || !m.contains(b.getWorld(), b.getX(), b.getY(), b.getZ())) {
                        continue;
                    }
                    boolean smelt = config.getBoolean("explosive-auto-smelt", true);
                    for (ItemStack drop : b.getDrops(tool)) {
                        ItemStack s = smelt ? smelted(drop) : drop.clone();
                        s.setAmount(randomRound(s.getAmount() * multiplier));
                        items.add(s);
                    }
                    b.setType(Material.AIR, false);
                    broken++;
                }
            }
        }
        if (broken > 0) {
            mines.blocksChanged(m, broken);
            center.getWorld().playSound(center.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.2f);
            center.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, center.getLocation().add(0.5, 0.5, 0.5), 1);
            give(p, items, center);
        }
    }

    /** Lifesteal: heals the attacker for part of the damage done. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getDamager();
        int lvl = heldLevel(p, Category.SWORD, "lifesteal");
        if (lvl <= 0) {
            return;
        }
        Def d = def(Category.SWORD, "lifesteal");
        double heal = event.getFinalDamage() * d.effect(lvl) / 100.0;
        double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + heal));
    }

    /** Items go into the inventory; whatever doesn't fit is dropped only if the config says so. */
    private void give(Player p, List<ItemStack> items, Block where) {
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (!left.isEmpty()) {
            if (config.getBoolean("drop-when-inventory-full", false)) {
                for (ItemStack s : left.values()) {
                    where.getWorld().dropItemNaturally(where.getLocation(), s);
                }
            } else {
                String msg = config.getString("messages.inventory-full", "&cYour inventory is full!");
                if (!msg.isEmpty()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Text.color(p, msg)));
                }
            }
        }
    }

    private Map<Material, ItemStack> smeltResults;

    /**
     * The furnace result for an ore (iron ore → iron ingot, gold ore → gold ingot...), using the
     * server's own furnace recipes. Anything that isn't an ore comes back unchanged.
     */
    private ItemStack smelted(ItemStack drop) {
        if (!drop.getType().name().endsWith("_ORE") && drop.getType() != Material.ANCIENT_DEBRIS) {
            return drop.clone();
        }
        if (smeltResults == null) {
            smeltResults = new HashMap<Material, ItemStack>();
            java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                org.bukkit.inventory.Recipe r = it.next();
                if (r instanceof org.bukkit.inventory.FurnaceRecipe) {
                    org.bukkit.inventory.FurnaceRecipe f = (org.bukkit.inventory.FurnaceRecipe) r;
                    Material in = f.getInput().getType();
                    if (!smeltResults.containsKey(in)) {
                        smeltResults.put(in, f.getResult());
                    }
                }
            }
        }
        ItemStack result = smeltResults.get(drop.getType());
        if (result == null) {
            return drop.clone();
        }
        return new ItemStack(result.getType(), drop.getAmount() * Math.max(1, result.getAmount()));
    }

    private static int randomRound(double v) {
        int whole = (int) Math.floor(v);
        return whole + (ThreadLocalRandom.current().nextDouble() < v - whole ? 1 : 0);
    }

    // ================================================================== opening

    @EventHandler(priority = EventPriority.HIGH)
    public void onTable(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE
                || event.getHand() != EquipmentSlot.HAND
                || !config.getBoolean("replace-enchanting-table", true)
                || !event.getPlayer().hasPermission("prisoncore.enchants")) {
            return;
        }
        event.setCancelled(true);
        EnchantMenu.openMain(this, event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("apply") || args[0].equalsIgnoreCase("remove"))) {
            applyCommand(sender, args);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can open the enchant menu. Staff: /enchanter apply <enchant> <level> <player>");
            return true;
        }
        EnchantMenu.openMain(this, (Player) sender);
        return true;
    }

    /**
     * /enchanter apply <enchant> <level> [player]  and  /enchanter remove <enchant> [player]
     * Puts any Enchanter enchant (including Token Finder, Explosive, Haste) on the item in the
     * player's hand, for free. For staff making kit and crate reward items.
     */
    private void applyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisoncore.enchants.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission to do that."));
            return;
        }
        boolean remove = args[0].equalsIgnoreCase("remove");
        int need = remove ? 2 : 3;
        if (args.length < need) {
            sender.sendMessage(Text.color(remove ? "&cUsage: /enchanter remove <enchant> [player]"
                    : "&cUsage: /enchanter apply <enchant> <level> [player]"));
            return;
        }
        Player target;
        if (args.length > need) {
            target = Bukkit.getPlayerExact(args[need]);
            if (target == null) {
                sender.sendMessage(Text.color("&c" + args[need] + " isn't online."));
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("Name a player: /enchanter " + args[0] + " ... <player>");
            return;
        }
        ItemStack item = target.getInventory().getItemInMainHand();
        Category c = category(item);
        if (c == null) {
            sender.sendMessage(Text.color("&cHold a pickaxe, sword or armor piece."));
            return;
        }
        Def d = def(c, args[1].toLowerCase(Locale.ROOT));
        if (d == null) {
            StringBuilder names = new StringBuilder();
            for (Def x : defs(c)) {
                names.append(names.length() == 0 ? "" : ", ").append(x.id);
            }
            sender.sendMessage(Text.color("&c" + args[1] + " isn't a " + c.key() + " enchant. Choose from: &f" + names));
            return;
        }
        if (!d.fits(item)) {
            sender.sendMessage(Text.color("&c" + d.name + " can't go on " + item.getType().name().toLowerCase(Locale.ROOT) + "."));
            return;
        }
        int level = 0;
        if (!remove) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                level = -1;
            }
            if (level < 1 || level > 1000) {
                sender.sendMessage(Text.color("&c" + args[2] + " isn't a valid level."));
                return;
            }
        }
        setLevel(item, d, level);
        target.getInventory().setItemInMainHand(item);
        sender.sendMessage(Text.color(remove ? "&7Removed &f" + d.name + "&7."
                : "&7Applied &f" + d.name + " " + levelText(level) + "&7" + (level > d.max ? " &8(above the Enchanter's max of " + d.max + ")" : "") + "."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (!sender.hasPermission("prisoncore.enchants.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String o : new String[] {"apply", "remove"}) {
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("apply") || args[0].equalsIgnoreCase("remove"))) {
            Category c = sender instanceof Player ? category(((Player) sender).getInventory().getItemInMainHand()) : null;
            for (Category cat : Category.values()) {
                if (c != null && cat != c) {
                    continue;
                }
                for (Def d : defs(cat)) {
                    if (d.id.startsWith(args[1].toLowerCase(Locale.ROOT)) && !out.contains(d.id)) {
                        out.add(d.id);
                    }
                }
            }
        } else if ((args.length == 3 && args[0].equalsIgnoreCase("remove")) || (args.length == 4 && args[0].equalsIgnoreCase("apply"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnchantMenu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            ((EnchantMenu) event.getView().getTopInventory().getHolder()).click(event.getRawSlot(), event.isShiftClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EnchantMenu) {
            event.setCancelled(true);
        }
    }

    // ================================================================== buying

    /** Buys up to {@code levels} levels. Returns how many were bought. */
    int buy(Player p, ItemStack item, Def d, int levels) {
        int bought = 0;
        for (int i = 0; i < levels; i++) {
            int lvl = level(item, d);
            if (lvl >= d.max) {
                break;
            }
            long cost = d.cost(lvl);
            boolean paid = d.category == Category.PICKAXE ? tokens.take(p, cost) : pvp.take(p, cost);
            if (!paid) {
                break;
            }
            setLevel(item, d, lvl + 1);
            bought++;
        }
        return bought;
    }

    long balance(Player p, Category c) {
        return c == Category.PICKAXE ? tokens.get(p.getUniqueId()) : pvp.get(p.getUniqueId());
    }

    String currency(Category c) {
        return c == Category.PICKAXE ? config.getString("currency.tokens", "&eTokens")
                : config.getString("currency.pvp-points", "&cPvP Points");
    }

    void actionBar(Player p, String msg) {
        if (msg != null && !msg.isEmpty()) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Text.color(p, msg)));
        }
    }
}
