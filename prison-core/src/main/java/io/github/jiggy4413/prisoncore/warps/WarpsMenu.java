package io.github.jiggy4413.prisoncore.warps;

import io.github.jiggy4413.prisoncore.mines.MineText;
import io.github.jiggy4413.prisoncore.util.ItemTemplate;
import io.github.jiggy4413.prisoncore.util.Text;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** The /warps menu for one player. Redrawn every second while open so mine stats stay live. */
public final class WarpsMenu implements InventoryHolder {

    private static final String CLOSE = "\u0000close";
    private static final String BACK = "\u0000back";

    private final Warps warps;
    private final Player player;
    /** Which page of warps-menu.yml this is, e.g. "main" or "mines". */
    private final String pageId;
    private Inventory inventory;
    /** Slot → entry key (or CLOSE / BACK). */
    private final Map<Integer, String> actions = new HashMap<Integer, String>();
    private final Map<String, Warps.Entry> entriesByKey = new HashMap<String, Warps.Entry>();
    private final Map<String, Boolean> lockedByKey = new HashMap<String, Boolean>();
    private long lastClick;

    WarpsMenu(Warps warps, Player player, String pageId) {
        this.warps = warps;
        this.player = player;
        this.pageId = pageId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void open() {
        ConfigurationSection page = warps.page(pageId);
        int rows = Math.max(1, Math.min(6, page.getInt("rows", 6)));
        inventory = Bukkit.createInventory(this, rows * 9, Text.color(player, page.getString("title", "Warps")));
        render();
        player.openInventory(inventory);
        sound("open");
    }

    void render() {
        FileConfiguration c = warps.config();
        ConfigurationSection page = warps.page(pageId);
        int size = inventory.getSize();
        Map<Integer, ItemStack> items = new HashMap<Integer, ItemStack>();
        actions.clear();
        entriesByKey.clear();
        lockedByKey.clear();

        Warps.Viewer viewer = warps.new Viewer(player);
        List<Warps.Entry> entries = warps.entries(pageId);
        for (Warps.Entry e : entries) {
            if (!e.exists || e.slot < 0 || e.slot >= size) {
                continue;
            }
            boolean locked = viewer.locked(e);
            String kind = e.mine != null ? "mine" : e.open != null ? "page" : "other";
            String templateName = "templates." + kind + (locked ? "-locked" : "");
            ConfigurationSection templateSection = c.getConfigurationSection(templateName);
            if (templateSection == null) {
                templateSection = c.getConfigurationSection("templates.other" + (locked ? "-locked" : ""));
            }
            ItemTemplate template = ItemTemplate.of(
                    e.section.getConfigurationSection(locked ? "locked-item" : "item"),
                    templateSection);
            ItemStack item = template.build(player, placeholders(e, c), null);
            if (item.getType() == Material.PLAYER_HEAD) {
                ItemTemplate.applyTexture(item, headTexture(e));
            }
            if (!locked && viewer.isCurrent(e)) {
                highlight(item, c);
            }

            if (c.getBoolean("numbered-icons", true) && e.mine != null) {
                int number = letterNumber(e.mine.id());
                if (number > 0) {
                    item.setAmount(number);
                }
            }
            items.put(e.slot, item);
            actions.put(e.slot, e.key);
            entriesByKey.put(e.key, e);
            lockedByKey.put(e.key, locked);
        }

        button(page, c, "close", CLOSE, items, size);
        if (warps.backPage(pageId) != null) {
            button(page, c, "back", BACK, items, size);
        }

        ConfigurationSection fillerSection = page.getConfigurationSection("filler");
        if (fillerSection == null) {
            fillerSection = c.getConfigurationSection("filler");
        }
        ItemStack filler = fillerSection == null ? null : ItemTemplate.of(fillerSection).build(player, null, null);
        for (int i = 0; i < size; i++) {
            ItemStack item = items.get(i);
            inventory.setItem(i, item != null ? item : filler);
        }
    }

    /** Places a button from the page's "buttons:" section, or the shared one at the top of the file. */
    private void button(ConfigurationSection page, FileConfiguration c, String name, String action,
                        Map<Integer, ItemStack> items, int size) {
        ConfigurationSection b = page.getConfigurationSection("buttons." + name);
        if (b == null) {
            b = c.getConfigurationSection("buttons." + name);
        }
        if (b == null) {
            return;
        }
        int slot = b.getInt("slot", -1);
        if (slot >= 0 && slot < size && !items.containsKey(slot)) {
            items.put(slot, ItemTemplate.of(b).build(player, null, null));
            actions.put(slot, action);
        }
    }

    private Map<String, String> placeholders(Warps.Entry e, FileConfiguration c) {
        Map<String, String> ph = new HashMap<String, String>();
        String display = e.section.getString("display", "");
        if (display == null || display.isEmpty()) {
            display = e.mine != null ? e.mine.display() : capitalize(e.key);
        }
        String icon = e.section.getString("icon", "");
        if ((icon == null || icon.isEmpty()) && headTexture(e) != null) {
            icon = "PLAYER_HEAD";
        }
        if (icon == null || icon.isEmpty()) {
            Material main = e.mine == null ? null : e.mine.mainBlock();
            icon = main != null ? main.name() : "ENDER_PEARL";
        }
        String rank = e.rank == null ? "" : e.rank;
        ph.put("{name}", display);
        ph.put("{icon}", icon);
        ph.put("{rank}", rank);
        ph.put("{warp}", e.warp != null ? e.warp : e.command != null ? "/" + e.command : e.open);
        ph.put("{player}", player.getName());
        ph.put("{requirement}", rank.isEmpty()
                ? c.getString("text.no-access", "&7You can't use this warp yet.")
                : c.getString("text.need-rank", "&7Reach rank &f{rank} &7to unlock.").replace("{rank}", rank));
        for (String field : MineText.FIELDS) {
            if (field.equals("name")) {
                continue; // {name} is the display set above, so a warp's own display isn't wiped
            }
            ph.put("{" + field + "}", e.mine == null ? "" : MineText.value(warps.mines(), e.mine, field));
        }
        return ph;
    }

    void handleClick(int rawSlot) {
        String key = actions.get(rawSlot);
        if (key == null) {
            return;
        }
        if (key.equals(CLOSE)) {
            player.closeInventory();
            return;
        }
        if (key.equals(BACK)) {
            openLater(warps.backPage(pageId));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClick < 600) {
            return; // ignore double clicks so the warp only runs once
        }
        lastClick = now;

        Warps.Entry e = entriesByKey.get(key);
        if (e == null) {
            return;
        }
        FileConfiguration c = warps.config();
        if (Boolean.TRUE.equals(lockedByKey.get(key))) {
            String msg = e.rank == null || e.rank.isEmpty()
                    ? c.getString("messages.locked", "&cYou can't use this warp yet.")
                    : c.getString("messages.locked-rank", "&cYou need rank &f{rank} &cto use this warp.");
            send(msg.replace("{rank}", e.rank == null ? "" : e.rank));
            sound("locked");
            return;
        }
        if (e.open != null) {
            openLater(e.open);
            return;
        }
        player.closeInventory();
        sound("teleport");
        player.performCommand(e.command != null ? e.command.replace("{player}", player.getName()) : "warp " + e.warp);
    }

    /** Opens another page on the next tick (opening a menu inside a click event isn't safe). */
    private void openLater(final String page) {
        if (page == null || !warps.hasPage(page)) {
            warps.plugin().getLogger().warning(Warps.FILE + ": there's no page called '" + page + "' under menus:");
            return;
        }
        Bukkit.getScheduler().runTask(warps.plugin(), new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    warps.open(player, page);
                }
            }
        });
    }

    private void send(String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(Text.color(player, message));
        }
    }

    private void sound(String key) {
        String name = warps.config().getString("sounds." + key, "");
        if (name == null || name.isEmpty()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 1f, 1f);
        } catch (IllegalArgumentException ex) {
            warps.plugin().getLogger().warning("Unknown sound in " + Warps.FILE + ": " + name);
        }
    }

    /** The entry's own "head:" texture, or the built-in letter head for a lettered mine. */
    private String headTexture(Warps.Entry e) {
        String own = e.section.getString("head", "");
        if (own != null && !own.isEmpty()) {
            return own;
        }
        if (e.mine == null || !warps.config().getBoolean("letter-heads", true)) {
            return null;
        }
        int n = letterNumber(e.mine.id());
        return n > 0 ? LetterHeads.texture((char) ('a' + n - 1)) : null;
    }

    /** Marks the mine for the player's current rank: a line on top of the lore, and a glint. */
    private void highlight(ItemStack item, FileConfiguration c) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.hasLore() ? new java.util.ArrayList<String>(meta.getLore()) : new java.util.ArrayList<String>();
        List<String> top = c.isList("text.current-mine") ? c.getStringList("text.current-mine")
                : java.util.Collections.singletonList(c.getString("text.current-mine", "&a&l\u2605 YOUR MINE \u2605"));
        for (int i = top.size() - 1; i >= 0; i--) {
            lore.add(0, Text.color(player, "&r" + top.get(i)));
        }
        meta.setLore(lore);
        String prefix = c.getString("text.current-mine-name", "&a\u2605 ");
        if (prefix != null && !prefix.isEmpty() && meta.hasDisplayName()) {
            meta.setDisplayName(Text.color(player, prefix) + meta.getDisplayName());
        }
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    /** a → 1 ... z → 26 for mines named like "amine"; 0 for anything else. */
    private static int letterNumber(String mineId) {
        String id = mineId.toLowerCase(Locale.ROOT);
        if (id.length() == 5 && id.endsWith("mine") && id.charAt(0) >= 'a' && id.charAt(0) <= 'z') {
            return id.charAt(0) - 'a' + 1;
        }
        if (id.length() == 1 && id.charAt(0) >= 'a' && id.charAt(0) <= 'z') {
            return id.charAt(0) - 'a' + 1;
        }
        return 0;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
