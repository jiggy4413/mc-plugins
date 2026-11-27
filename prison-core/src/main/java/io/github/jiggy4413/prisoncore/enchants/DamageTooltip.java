package io.github.jiggy4413.prisoncore.enchants;

import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.collect.Multimap;

/**
 * Brings back the 1.16.5 weapon tooltip: the "Attack Damage" number includes Sharpness.
 *
 * Minecraft 1.21 stopped adding enchantments to that number. For swords, axes and other melee tools this
 * hides the vanilla stats and writes the same lines into the lore, in vanilla's style:
 *     When in Main Hand:
 *      11 Attack Damage
 *      1.6 Attack Speed
 * The damage is the real hit damage: OldCombatMechanics' old-tool-damage values when that module is on
 * (so a netherite axe shows 8, not 10), plus Sharpness (1.9+ or old formula, as OCM is set).
 *
 * PrisonCore special items that write their own damage lore (frost weapons) are left alone.
 * Turn off with damage-tooltip: false in enchants.yml.
 */
public final class DamageTooltip implements Listener {

    private static final DecimalFormat NUM = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final JavaPlugin plugin;
    private final NamespacedKey linesKey;
    private boolean enabled = true;
    /** OCM old-tool-damage values by Material (only when that module is on). */
    private final Map<Material, Double> ocmDamage = new HashMap<Material, Double>();
    private boolean oldSharpness;

    public DamageTooltip(JavaPlugin plugin) {
        this.plugin = plugin;
        this.linesKey = new NamespacedKey(plugin, "damage_lore_lines");
    }

    /** Re-reads the toggle and OldCombatMechanics' tool damage settings. */
    public void load(boolean enabled) {
        this.enabled = enabled;
        ocmDamage.clear();
        oldSharpness = false;
        File f = new File(plugin.getDataFolder().getParentFile(), "OldCombatMechanics" + File.separator + "config.yml");
        if (!f.isFile() || Bukkit.getPluginManager().getPlugin("OldCombatMechanics") == null) {
            return;
        }
        YamlConfiguration ocm = YamlConfiguration.loadConfiguration(f);
        if (!ocm.getStringList("always_enabled_modules").contains("old-tool-damage")) {
            return;
        }
        oldSharpness = ocm.getBoolean("old-tool-damage.old-sharpness", false);
        org.bukkit.configuration.ConfigurationSection d = ocm.getConfigurationSection("old-tool-damage.damages");
        if (d == null) {
            return;
        }
        for (String key : d.getKeys(false)) {
            Material m = Material.matchMaterial(modernName(key));
            if (m != null) {
                ocmDamage.put(m, d.getDouble(key));
            }
        }
    }

    /** OCM uses old names: GOLD_SWORD, WOOD_AXE, DIAMOND_SPADE. */
    private static String modernName(String key) {
        String k = key.toUpperCase(Locale.ROOT);
        if (k.startsWith("GOLD_")) {
            k = "GOLDEN_" + k.substring(5);
        } else if (k.startsWith("WOOD_")) {
            k = "WOODEN_" + k.substring(5);
        }
        if (k.endsWith("_SPADE")) {
            k = k.substring(0, k.length() - 6) + "_SHOVEL";
        }
        return k;
    }

    private static boolean isWeapon(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || m == Material.TRIDENT || m == Material.MACE;
    }

    // ------------------------------------------------------------------ the tooltip

    /** Updates one item's damage lines in place. Returns true if anything changed. */
    public boolean update(ItemStack item) {
        if (item == null || !isWeapon(item.getType())) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Integer owned = meta.getPersistentDataContainer().get(linesKey, PersistentDataType.INTEGER);
        // items whose stats someone else hid (PrisonCore frost weapons with their own damage lore): leave alone
        if (owned == null && meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
            return false;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        if (owned != null) {
            for (int i = 0; i < owned && !lore.isEmpty(); i++) {
                lore.remove(lore.size() - 1);
            }
        }
        if (!enabled) {
            if (owned == null) {
                return false;
            }
            meta.setLore(lore.isEmpty() ? null : lore);
            meta.getPersistentDataContainer().remove(linesKey);
            meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            return true;
        }
        double[] stats = stats(item.getType(), meta);
        if (stats == null) {
            return false;
        }
        int sharp = item.getEnchantmentLevel(Enchantment.SHARPNESS);
        double damage = stats[0] + (sharp <= 0 ? 0 : oldSharpness ? 1.25 * sharp : 0.5 * sharp + 0.5);
        List<String> ours = new ArrayList<String>();
        ours.add("");
        ours.add(Text.color("&7When in Main Hand:"));
        ours.add(Text.color("&2 " + NUM.format(damage) + " Attack Damage"));
        ours.add(Text.color("&2 " + NUM.format(stats[1]) + " Attack Speed"));
        List<String> before = meta.hasLore() ? meta.getLore() : null;
        lore.addAll(ours);
        if (owned != null && owned == ours.size() && lore.equals(before)) {
            return false;                                       // already up to date
        }
        // hiding stats can make the game drop an item's default attributes, so write them out first
        if (!meta.hasAttributeModifiers()) {
            Multimap<Attribute, AttributeModifier> defaults = item.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND);
            if (defaults != null && !defaults.isEmpty()) {
                meta.setAttributeModifiers(defaults);
            }
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(linesKey, PersistentDataType.INTEGER, ours.size());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return true;
    }

    /** {damage, attack speed} as the player experiences them, or null if the item has no attack damage. */
    private double[] stats(Material m, ItemMeta meta) {
        Multimap<Attribute, AttributeModifier> mods = meta.hasAttributeModifiers()
                ? meta.getAttributeModifiers() : m.getDefaultAttributeModifiers(EquipmentSlot.HAND);
        double damage = 1, speed = 4;
        boolean any = false;
        if (mods != null) {
            damage += sum(mods.get(Attribute.ATTACK_DAMAGE));
            speed += sum(mods.get(Attribute.ATTACK_SPEED));
            any = !mods.get(Attribute.ATTACK_DAMAGE).isEmpty();
        }
        Double ocm = ocmDamage.get(m);
        if (ocm != null) {
            damage = ocm;                                         // OCM replaces the weapon's base damage
            any = true;
        }
        return any ? new double[] {damage, speed} : null;
    }

    private static double sum(Collection<AttributeModifier> list) {
        double t = 0;
        if (list != null) {
            for (AttributeModifier a : list) {
                if (a.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    t += a.getAmount();
                }
            }
        }
        return t;
    }

    public void updateInventory(HumanEntity p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] items = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && update(items[i])) {
                changed = true;
            }
        }
        if (changed) {
            inv.setContents(items);
        }
    }

    public void updateAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateInventory(p);
        }
    }

    // ------------------------------------------------------------------ when to refresh

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updateInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        PlayerInventory inv = event.getPlayer().getInventory();
        ItemStack it = inv.getItem(event.getNewSlot());
        if (it != null && update(it)) {
            inv.setItem(event.getNewSlot(), it);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        updateInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(final EnchantItemEvent event) {
        // the enchants are put on after this event; refresh the item a tick later
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                ItemStack it = event.getInventory().getItem(0);
                if (it != null && update(it)) {
                    event.getInventory().setItem(0, it);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result != null && update(result)) {
            event.setResult(result);
        }
    }
}
