package io.github.jiggy4413.prisoncore.servermenu;

import io.github.jiggy4413.prisoncore.util.ItemTemplate;
import io.github.jiggy4413.prisoncore.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The server menu: a compass locked in every player's hotbar that opens menus defined in
 * server-menu.yml. Each menu item runs a list of actions when clicked.
 */
public final class ServerMenus {

    public static final String FILE = "server-menu.yml";

    private final JavaPlugin plugin;
    private final File file;
    private final NamespacedKey compassKey;
    private YamlConfiguration config;

    public ServerMenus(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE);
        // the key name never changes, so compasses keep working across updates
        this.compassKey = new NamespacedKey(plugin, "server_menu_compass");
    }

    /** Loads server-menu.yml. Returns a problem description, or null. */
    public String load() {
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0];
            plugin.getLogger().severe(FILE + " has a mistake: " + msg);
            if (config != null) {
                return FILE + " has a mistake, kept the old settings: " + msg;
            }
        }
        config = loaded;
        return null;
    }

    // =================================================================== the compass

    public boolean compassEnabled() {
        // no compass section (e.g. the file failed to load on first start) = no compass
        return config.isConfigurationSection("compass") && config.getBoolean("compass.enabled", true);
    }

    /** Hotbar slot 0-8 (config uses 1-9 so it matches the number keys). */
    public int compassSlot() {
        return Math.max(1, Math.min(9, config.getInt("compass.slot", 9))) - 1;
    }

    public boolean isCompass(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    public ItemStack createCompass(Player viewer) {
        ConfigurationSection s = config.getConfigurationSection("compass");
        ItemStack item = ItemTemplate.of(s).build(viewer,
                Collections.singletonMap("{player}", viewer.getName()), null);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        item.setAmount(1);
        return item;
    }

    /**
     * Makes sure the player has exactly one compass, in its slot. Anything else in that slot is
     * moved to a free slot (or dropped at their feet if the inventory is full).
     */
    public void ensureCompass(Player p) {
        PlayerInventory inv = p.getInventory();
        if (!compassEnabled() || p.getGameMode() == GameMode.SPECTATOR) {
            if (!compassEnabled()) {
                removeCompasses(p);
            }
            return;
        }
        int slot = compassSlot();
        // remove stray copies anywhere else (including the off hand and cursor-less slots)
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != slot && isCompass(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isCompass(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
        }
        ItemStack current = inv.getItem(slot);
        if (isCompass(current)) {
            // refresh its look in case the config changed
            inv.setItem(slot, createCompass(p));
            return;
        }
        if (current != null && current.getType() != org.bukkit.Material.AIR) {
            inv.setItem(slot, null);
            Map<Integer, ItemStack> left = inv.addItem(current);
            for (ItemStack drop : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
                p.sendMessage(Text.color(p, config.getString("messages.slot-item-dropped",
                        "&eYour inventory was full, so an item was dropped at your feet to make room for the menu.")));
            }
        }
        inv.setItem(slot, createCompass(p));
    }

    public void removeCompasses(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) {
                inv.setItem(i, null);
            }
        }
    }

    // =================================================================== menus

    public boolean hasMenu(String id) {
        return config.isConfigurationSection("menus." + id);
    }

    public String mainMenu() {
        return config.getString("compass.opens", "main");
    }

    public void open(Player p, String id) {
        ConfigurationSection m = config.getConfigurationSection("menus." + id);
        if (m == null) {
            p.sendMessage(Text.color(p, "&cThat menu doesn't exist: " + id));
            plugin.getLogger().warning("Menu '" + id + "' is used but not defined in " + FILE);
            return;
        }
        int rows = Math.max(1, Math.min(6, m.getInt("rows", 5)));
        int size = rows * 9;
        ServerMenu menu = new ServerMenu(id);
        String title = m.getString("title", id).replace("{player}", p.getName());
        Inventory inv = Bukkit.createInventory(menu, size, Text.color(p, title));
        menu.inventory = inv;

        ConfigurationSection items = m.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection it = items.getConfigurationSection(key);
                if (it == null) {
                    continue;
                }
                placeItem(p, menu, it, size);
            }
        }

        ConfigurationSection filler = m.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", true)) {
            ItemStack fill = ItemTemplate.of(filler).build(p, null, null);
            for (int i = 0; i < size; i++) {
                if (inv.getItem(i) == null) {
                    inv.setItem(i, fill);
                }
            }
        }
        p.openInventory(inv);
        sound(p, config.getString("sounds.open", ""));
    }

    private void placeItem(Player p, ServerMenu menu, ConfigurationSection it, int size) {
        List<Integer> slots = new ArrayList<Integer>();
        if (it.isList("slots")) {
            for (Object o : it.getList("slots")) {
                try {
                    slots.add(Integer.parseInt(String.valueOf(o).trim()));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        } else {
            slots.add(it.getInt("slot", -1));
        }

        String perm = it.getString("permission", "");
        boolean allowed = perm == null || perm.isEmpty() || p.hasPermission(perm);
        ConfigurationSection locked = it.getConfigurationSection("locked");
        if (!allowed && locked == null) {
            return; // hidden from players without the permission
        }
        Map<String, String> ph = Collections.singletonMap("{player}", p.getName());
        ItemStack item = allowed
                ? ItemTemplate.of(it).build(p, ph, null)
                : ItemTemplate.of(locked, it).build(p, ph, null);

        for (int slot : slots) {
            if (slot < 0 || slot >= size) {
                continue;
            }
            menu.inventory.setItem(slot, item);
            if (allowed) {
                List<String> actions = it.getStringList("actions");
                menu.actions.put(slot, actions);
                menu.closeOnClick.put(slot, it.getBoolean("close", !containsMenuAction(actions)));
            } else {
                menu.lockedMessages.put(slot, locked.getString("message",
                        config.getString("messages.locked", "&cYou haven't unlocked this yet.")));
            }
        }
    }

    private static boolean containsMenuAction(List<String> actions) {
        for (String a : actions) {
            if (a.trim().toLowerCase(Locale.ROOT).startsWith("[menu]")) {
                return true;
            }
        }
        return false;
    }

    /** Runs a clicked item's actions. Called one tick after the click. */
    void click(Player p, ServerMenu menu, int slot) {
        String locked = menu.lockedMessages.get(slot);
        if (locked != null) {
            if (!locked.isEmpty()) {
                p.sendMessage(Text.color(p, locked));
            }
            sound(p, config.getString("sounds.locked", ""));
            return;
        }
        List<String> actions = menu.actions.get(slot);
        if (actions == null) {
            return;
        }
        sound(p, config.getString("sounds.click", ""));
        Boolean close = menu.closeOnClick.get(slot);
        if (close != null && close) {
            p.closeInventory();
        }
        for (String raw : actions) {
            runAction(p, raw);
        }
    }

    private void runAction(Player p, String raw) {
        String a = raw.trim();
        String lower = a.toLowerCase(Locale.ROOT);
        String arg = a.indexOf(']') >= 0 ? a.substring(a.indexOf(']') + 1).trim() : a;
        arg = arg.replace("{player}", p.getName());
        if (arg.startsWith("/")) {
            arg = arg.substring(1);
        }
        if (lower.startsWith("[player]")) {
            p.performCommand(arg);
        } else if (lower.startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), arg);
        } else if (lower.startsWith("[menu]")) {
            open(p, arg);
        } else if (lower.startsWith("[close]")) {
            p.closeInventory();
        } else if (lower.startsWith("[message]")) {
            p.sendMessage(Text.color(p, arg));
        } else if (lower.startsWith("[sound]")) {
            sound(p, arg);
        } else if (!a.isEmpty()) {
            // no prefix: treat as a player command
            p.performCommand(arg);
        }
    }

    String message(String key, String def) {
        return config.getString("messages." + key, def);
    }

    private void sound(Player p, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        try {
            p.playSound(p.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 1f, 1f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown sound in " + FILE + ": " + name);
        }
    }
}
