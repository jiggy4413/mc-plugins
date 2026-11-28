package io.github.jiggy4413.prisoncore.soulbound;

import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Soulbound Scrolls. Click a scroll onto a weapon, tool or armor piece in your inventory to make it Soulbound:
 * the next time you die it stays with you instead of dropping. That uses up the protection (one save), so the
 * item needs a new scroll to be protected again.
 *
 * Give scrolls with /prisoncore soulbound <player> [amount] (crate rewards, Tebex).
 */
public final class Soulbound implements Listener {

    private static final String FILE = "soulbound.yml";

    private final JavaPlugin plugin;
    private final NamespacedKey scrollKey;
    private final NamespacedKey boundKey;
    /** The enchant lore lines PrisonCore keeps at the top of the lore; the Soulbound line goes right after them. */
    private final NamespacedKey enchantLinesKey;
    private YamlConfiguration config = new YamlConfiguration();
    private final Map<UUID, Integer> saved = new HashMap<UUID, Integer>();

    public Soulbound(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scrollKey = new NamespacedKey(plugin, "soulbound_scroll");
        this.boundKey = new NamespacedKey(plugin, "soulbound");
        this.enchantLinesKey = new NamespacedKey(plugin, "enchant_lore_lines");
    }

    /** Reads soulbound.yml. Returns a problem message, or null. */
    public String load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.load(file);
        } catch (Exception e) {
            return "soulbound.yml has a mistake: " + e.getMessage();
        }
        config = c;
        return null;
    }

    private String msg(String key, String def) {
        return Text.color(config.getString("messages." + key, def));
    }

    // ================================================================== the scroll

    public ItemStack scroll(int amount) {
        ConfigurationSection s = config.getConfigurationSection("scroll");
        Material mat = Material.matchMaterial(s == null ? "PAPER" : s.getString("material", "PAPER"));
        ItemStack it = new ItemStack(mat == null ? Material.PAPER : mat, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Text.color(s == null ? "&d&lSoulbound Scroll" : s.getString("name", "&d&lSoulbound Scroll")));
        List<String> lore = new ArrayList<String>();
        for (String l : s == null ? new ArrayList<String>() : s.getStringList("lore")) {
            lore.add(Text.color(l));
        }
        m.setLore(lore);
        String model = s == null ? "" : s.getString("item-model", "");
        if (model != null && !model.isEmpty()) {
            NamespacedKey k = NamespacedKey.fromString(model);
            if (k != null) {
                m.setItemModel(k);
            }
        }
        if (s == null || s.getBoolean("glow", true)) {
            m.addEnchant(Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        m.getPersistentDataContainer().set(scrollKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isScroll(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(scrollKey, PersistentDataType.BYTE);
    }

    public boolean isBound(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(boundKey, PersistentDataType.BYTE);
    }

    /** Weapons, tools and armor: anything that can lose durability (also unbreakable crate gear), plus shields. */
    static boolean bindable(ItemStack it) {
        if (it == null || it.getType().isAir() || it.getAmount() != 1) {
            return false;
        }
        return it.getType().getMaxDurability() > 0;
    }

    private String line() {
        return Text.color(config.getString("lore-line", "&d✦ Soulbound"));
    }

    /** Marks an item Soulbound and adds the lore line (right after the enchant lines). */
    public void bind(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(boundKey, PersistentDataType.BYTE, (byte) 1);
        List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
        Integer top = pdc.get(enchantLinesKey, PersistentDataType.INTEGER);
        int at = Math.max(0, Math.min(lore.size(), top == null ? 0 : top));
        lore.add(at, line());
        m.setLore(lore);
        it.setItemMeta(m);
    }

    /** Takes the Soulbound mark (and its lore line) off again. */
    public void unbind(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().remove(boundKey);
        if (m.hasLore()) {
            List<String> lore = new ArrayList<String>(m.getLore());
            lore.remove(line());
            m.setLore(lore.isEmpty() ? null : lore);
        }
        it.setItemMeta(m);
    }

    /** /prisoncore soulbound <player> [amount] */
    public void command(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisoncore.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission to do that."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.color("&e/prisoncore soulbound <player> [amount] &7- give Soulbound Scrolls"));
            return;
        }
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) {
            sender.sendMessage(Text.color("&c" + args[1] + " isn't online."));
            return;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Text.color("&cAmount must be a number."));
                return;
            }
        }
        for (ItemStack left : p.getInventory().addItem(scroll(amount)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.sendMessage(msg("received", "&dYou received {amount} Soulbound Scroll(s)! &7Click one onto a weapon, tool or armor piece.")
                .replace("{amount}", String.valueOf(amount)));
        if (!(sender instanceof Player) || sender != p) {
            sender.sendMessage(Text.color("&aGave " + amount + " Soulbound Scroll(s) to " + p.getName() + "."));
        }
    }

    // ================================================================== applying a scroll

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onApply(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!isScroll(cursor)) {
            return;
        }
        if (!(event.getClickedInventory() instanceof PlayerInventory)
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir() || isScroll(target)) {
            return;   // normal click: pick up / place / stack the scrolls
        }
        Player p = (Player) event.getWhoClicked();
        event.setCancelled(true);
        if (!bindable(target)) {
            p.sendMessage(msg("invalid", "&cSoulbound Scrolls only work on weapons, tools and armor."));
            return;
        }
        if (isBound(target)) {
            p.sendMessage(msg("already", "&cThat item is already Soulbound."));
            return;
        }
        ItemStack bound = target.clone();
        bind(bound);
        event.setCurrentItem(bound);
        if (cursor.getAmount() <= 1) {
            p.setItemOnCursor(null);
        } else {
            ItemStack rest = cursor.clone();
            rest.setAmount(cursor.getAmount() - 1);
            p.setItemOnCursor(rest);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
        p.sendMessage(msg("applied", "&dYour item is now Soulbound! &7It stays with you the next time you die."));
    }

    // ================================================================== dying

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Player p = event.getEntity();
        if (config.getStringList("disabled-worlds").contains(p.getWorld().getName())) {
            return;
        }
        int n = 0;
        for (Iterator<ItemStack> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack drop = it.next();
            if (isBound(drop)) {
                it.remove();
                ItemStack keep = drop.clone();
                unbind(keep);          // one save: the protection is used up
                event.getItemsToKeep().add(keep);
                n++;
            }
        }
        if (n > 0) {
            saved.put(p.getUniqueId(), n);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Integer n = saved.remove(event.getPlayer().getUniqueId());
        if (n != null) {
            event.getPlayer().sendMessage(msg("saved", "&dSoulbound saved {count} item(s)! &7They're not protected anymore: apply a new scroll to protect them again.")
                    .replace("{count}", String.valueOf(n)));
        }
    }
}
