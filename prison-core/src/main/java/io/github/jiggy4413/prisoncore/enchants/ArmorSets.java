package io.github.jiggy4413.prisoncore.enchants;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Armor sets (items.yml `set: winter`). A piece that belongs to a set only gives its armor enchants
 * while the player wears all four pieces of that same set.
 */
public final class ArmorSets {

    /** Same key as new NamespacedKey(PrisonCore, "armor_set"); fixed so it can be used statically. */
    public static final NamespacedKey KEY = new NamespacedKey("prisoncore", "armor_set");

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorSets() {
    }

    /** The set a piece belongs to, or null. */
    public static String setOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String s = meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return s == null || s.isEmpty() ? null : s.toLowerCase(Locale.ROOT);
    }

    /** The set the player is wearing all four pieces of, or null. */
    public static String fullSet(Player p) {
        String set = null;
        for (EquipmentSlot slot : ARMOR) {
            String s = setOf(p.getInventory().getItem(slot));
            if (s == null || (set != null && !set.equals(s))) {
                return null;
            }
            set = s;
        }
        return set;
    }

    /** Whether this worn piece's enchants count right now. Pieces that aren't part of a set always count. */
    public static boolean active(Player p, ItemStack piece) {
        String s = setOf(piece);
        return s == null || s.equals(fullSet(p));
    }
}
