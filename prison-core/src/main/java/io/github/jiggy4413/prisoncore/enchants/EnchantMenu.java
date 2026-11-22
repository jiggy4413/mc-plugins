package io.github.jiggy4413.prisoncore.enchants;

import io.github.jiggy4413.prisoncore.tokens.Tokens;
import io.github.jiggy4413.prisoncore.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The enchant menus: the main page (pickaxe, sword, armor and both balances), the armor page
 * (the four pieces the player is wearing) and an upgrade page for one item.
 * The item being upgraded is always the real one in the player's hand or armor slot.
 */
public final class EnchantMenu implements InventoryHolder {

    private enum Page { MAIN, ARMOR, ITEM }

    private static final int[] ENCHANT_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int TOKENS_SLOT = 3;
    private static final int PVP_SLOT = 5;
    private static final int BACK_SLOT = 36;
    private static final int CLOSE_SLOT = 40;

    private final Enchants enchants;
    private final Player player;
    private final Page page;
    /** For ITEM pages: which slot holds the item (main hand, or an armor slot). */
    private final EquipmentSlot slot;
    private final Enchants.Category category;
    private Inventory inventory;
    private final Map<Integer, Runnable> actions = new HashMap<Integer, Runnable>();
    private final Map<Integer, Enchants.Def> enchantAt = new HashMap<Integer, Enchants.Def>();
    private long lastClick;

    private EnchantMenu(Enchants enchants, Player player, Page page, EquipmentSlot slot, Enchants.Category category) {
        this.enchants = enchants;
        this.player = player;
        this.page = page;
        this.slot = slot;
        this.category = category;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ opening

    static void openMain(Enchants e, Player p) {
        new EnchantMenu(e, p, Page.MAIN, null, null).open();
    }

    private void openLater(final EnchantMenu next) {
        Bukkit.getScheduler().runTask(enchants.plugin(), new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    next.open();
                }
            }
        });
    }

    private void open() {
        FileConfiguration c = enchants.config();
        String title;
        if (page == Page.MAIN) {
            title = c.getString("menu.title", "<gradient:#FFAA00:#FFFF55>&lEnchanter</gradient>");
        } else if (page == Page.ARMOR) {
            title = c.getString("menu.armor-title", "<gradient:#FFAA00:#FFFF55>&lArmor</gradient>");
        } else {
            title = c.getString("menu.item-title", "<gradient:#FFAA00:#FFFF55>&lUpgrade {item}</gradient>")
                    .replace("{item}", nice(category.name()));
        }
        inventory = Bukkit.createInventory(this, 45, Text.color(player, title));
        render();
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        actions.clear();
        enchantAt.clear();
        ItemStack filler = icon(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(TOKENS_SLOT, icon(Material.SUNFLOWER, "&e&lTokens",
                lines("&7Balance: &e" + Tokens.format(enchants.tokens().get(player.getUniqueId())),
                        "", "&7Find tokens while mining.", "&7Spend them on pickaxe enchants.")));
        inventory.setItem(PVP_SLOT, icon(Material.NETHER_STAR, "&c&lPvP Points",
                lines("&7Balance: &c" + Tokens.format(enchants.pvp().get(player.getUniqueId())),
                        "", "&7Get 1 point for every kill.", "&7Spend them on sword and armor enchants.")));
        inventory.setItem(CLOSE_SLOT, icon(Material.BARRIER, "&cClose", null));
        actions.put(CLOSE_SLOT, new Runnable() {
            @Override
            public void run() {
                player.closeInventory();
            }
        });

        if (page == Page.MAIN) {
            renderMain();
        } else if (page == Page.ARMOR) {
            renderArmor();
        } else {
            renderItem();
        }
    }

    private void renderMain() {
        final ItemStack hand = player.getInventory().getItemInMainHand();
        Enchants.Category held = Enchants.category(hand);

        inventory.setItem(20, icon(Material.NETHERITE_PICKAXE, "&e&lPickaxe Enchants",
                lines("&7Spend &eTokens &7on the pickaxe", "&7in your hand.", "",
                        held == Enchants.Category.PICKAXE ? "&aClick to upgrade" : "&cHold a pickaxe to upgrade it")));
        actions.put(20, itemPage(EquipmentSlot.HAND, Enchants.Category.PICKAXE, "&cHold the pickaxe you want to upgrade."));

        inventory.setItem(22, icon(Material.NETHERITE_SWORD, "&c&lSword Enchants",
                lines("&7Spend &cPvP Points &7on the sword", "&7in your hand.", "",
                        held == Enchants.Category.SWORD ? "&aClick to upgrade" : "&cHold a sword to upgrade it")));
        actions.put(22, itemPage(EquipmentSlot.HAND, Enchants.Category.SWORD, "&cHold the sword you want to upgrade."));

        inventory.setItem(24, icon(Material.NETHERITE_CHESTPLATE, "&b&lArmor Enchants",
                lines("&7Spend &cPvP Points &7on the armor", "&7you're wearing.", "", "&aClick to choose a piece")));
        actions.put(24, new Runnable() {
            @Override
            public void run() {
                openLater(new EnchantMenu(enchants, player, Page.ARMOR, null, null));
            }
        });
    }

    private void renderArmor() {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Material[] empty = {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
        String[] names = {"Helmet", "Chestplate", "Leggings", "Boots"};
        int[] at = {19, 21, 23, 25};
        for (int i = 0; i < 4; i++) {
            ItemStack worn = player.getInventory().getItem(slots[i]);
            if (Enchants.category(worn) == Enchants.Category.ARMOR) {
                ItemStack shown = worn.clone();
                ItemMeta meta = shown.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
                lore.add("");
                lore.add(Text.color("&aClick to upgrade"));
                meta.setLore(lore);
                shown.setItemMeta(meta);
                inventory.setItem(at[i], shown);
            } else {
                inventory.setItem(at[i], icon(empty[i], "&7" + names[i], lines("&cNot wearing one.", "&7Put it on to upgrade it.")));
            }
            actions.put(at[i], itemPage(slots[i], Enchants.Category.ARMOR, "&cPut on the " + names[i].toLowerCase() + " you want to upgrade."));
        }
        back(new EnchantMenu(enchants, player, Page.MAIN, null, null));
    }

    private void renderItem() {
        ItemStack item = target();
        if (item == null) {
            return;
        }
        inventory.setItem(13, item.clone());
        int i = 0;
        String unit = enchants.currency(category);
        // enchants with their own "slot:" go there; the rest fill the free enchant slots in order
        java.util.Set<Integer> taken = new java.util.HashSet<Integer>();
        for (Enchants.Def d : enchants.defs(category)) {
            if (d.inMenu && d.fits(item) && d.slot >= 0 && d.slot < inventory.getSize()) {
                taken.add(d.slot);
            }
        }
        for (final Enchants.Def d : enchants.defs(category)) {
            if (!d.fits(item) || !d.inMenu) {
                continue;
            }
            int at;
            if (d.slot >= 0 && d.slot < inventory.getSize()) {
                at = d.slot;
            } else {
                while (i < ENCHANT_SLOTS.length && taken.contains(ENCHANT_SLOTS[i])) {
                    i++;
                }
                if (i >= ENCHANT_SLOTS.length) {
                    continue;
                }
                at = ENCHANT_SLOTS[i++];
            }
            int lvl = enchants.level(item, d);
            List<String> lore = new ArrayList<String>();
            for (String line : d.description) {
                lore.add("&7" + line);
            }
            lore.add("");
            lore.add("&7Level: &f" + (lvl > 0 ? enchants.levelText(lvl) : "0") + "&7/&f" + enchants.levelText(d.max));
            if (lvl >= d.max) {
                lore.add("&a&lMAXED");
            } else {
                long cost = d.cost(lvl);
                boolean afford = enchants.balance(player, category) >= cost;
                lore.add("&7Next level: " + (afford ? "&a" : "&c") + Tokens.format(cost) + " " + unit);
                lore.add("");
                lore.add("&eClick &7to buy 1 level");
                lore.add("&eShift-click &7to buy up to 10");
            }
            ItemStack icon = icon(d.icon, (lvl >= d.max ? "&a" : "&b") + "&l" + d.name + (lvl > 0 ? " &7" + enchants.levelText(lvl) : ""), lore);
            inventory.setItem(at, icon);
            enchantAt.put(at, d);
        }
        if (category == Enchants.Category.ARMOR) {
            back(new EnchantMenu(enchants, player, Page.ARMOR, null, null));
        } else {
            back(new EnchantMenu(enchants, player, Page.MAIN, null, null));
        }
    }

    private void back(final EnchantMenu to) {
        inventory.setItem(BACK_SLOT, icon(Material.ARROW, "&7Back", null));
        actions.put(BACK_SLOT, new Runnable() {
            @Override
            public void run() {
                openLater(to);
            }
        });
    }

    private Runnable itemPage(final EquipmentSlot where, final Enchants.Category cat, final String missing) {
        return new Runnable() {
            @Override
            public void run() {
                if (Enchants.category(player.getInventory().getItem(where)) != cat) {
                    player.sendMessage(Text.color(player, missing));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                openLater(new EnchantMenu(enchants, player, Page.ITEM, where, cat));
            }
        };
    }

    /** The real item being upgraded, or null if it's gone (then the menu goes back). */
    private ItemStack target() {
        ItemStack item = player.getInventory().getItem(slot);
        if (Enchants.category(item) != category) {
            player.sendMessage(Text.color(player, "&cThat item isn't there anymore."));
            openLater(new EnchantMenu(enchants, player, Page.MAIN, null, null));
            return null;
        }
        return item;
    }

    // ------------------------------------------------------------------ clicks

    void click(int rawSlot, boolean shift) {
        long now = System.currentTimeMillis();
        if (now - lastClick < 150) {
            return;
        }
        lastClick = now;
        Runnable action = actions.get(rawSlot);
        if (action != null) {
            action.run();
            return;
        }
        Enchants.Def d = enchantAt.get(rawSlot);
        if (d == null) {
            return;
        }
        ItemStack item = target();
        if (item == null) {
            return;
        }
        int lvl = enchants.level(item, d);
        if (lvl >= d.max) {
            enchants.actionBar(player, "&a" + d.name + " is already maxed.");
            return;
        }
        int bought = enchants.buy(player, item, d, shift ? 10 : 1);
        if (bought == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            enchants.actionBar(player, "&cYou need " + Tokens.format(d.cost(lvl)) + " " + enchants.currency(category) + "&c.");
            return;
        }
        player.getInventory().setItem(slot, item);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        enchants.actionBar(player, "&a" + d.name + " " + enchants.levelText(enchants.level(item, d)) + "!");
        render();
    }

    // ------------------------------------------------------------------ helpers

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.color(player, name));
        if (lore != null) {
            List<String> colored = new ArrayList<String>();
            for (String l : lore) {
                colored.add(Text.color(player, l));
            }
            meta.setLore(colored);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static List<String> lines(String... l) {
        List<String> out = new ArrayList<String>();
        for (String s : l) {
            out.add(s);
        }
        return out;
    }

    private static String nice(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
