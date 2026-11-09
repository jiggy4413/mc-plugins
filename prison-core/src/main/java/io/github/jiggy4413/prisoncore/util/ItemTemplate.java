package io.github.jiggy4413.prisoncore.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * A menu item described in config. Built from several config sections layered on top of each
 * other: the first section that sets a field wins. That's how a single rank can override just
 * its material while keeping the shared name and lore.
 *
 * Fields: material, name, lore, glow, model-data, amount, skull ("viewer" = the player's head).
 */
public final class ItemTemplate {

    private String material;
    private String name;
    private List<String> lore;
    private Boolean glow;
    private Integer modelData;
    private Integer amount;
    private String skull;

    private ItemTemplate() {
    }

    public static ItemTemplate of(ConfigurationSection... layers) {
        ItemTemplate t = new ItemTemplate();
        for (ConfigurationSection s : layers) {
            if (s == null) {
                continue;
            }
            if (t.material == null && s.isString("material")) {
                t.material = s.getString("material");
            }
            if (t.name == null && s.isString("name")) {
                t.name = s.getString("name");
            }
            if (t.lore == null && s.contains("lore")) {
                t.lore = s.isList("lore") ? s.getStringList("lore")
                        : Collections.singletonList(s.getString("lore"));
            }
            if (t.glow == null && s.isBoolean("glow")) {
                t.glow = s.getBoolean("glow");
            }
            if (t.modelData == null && s.isInt("model-data")) {
                t.modelData = s.getInt("model-data");
            }
            if (t.amount == null && s.isInt("amount")) {
                t.amount = s.getInt("amount");
            }
            if (t.skull == null && s.isString("skull")) {
                t.skull = s.getString("skull");
            }
        }
        return t;
    }

    public ItemTemplate withGlow(boolean value) {
        this.glow = value;
        return this;
    }

    /**
     * @param placeholders single-value placeholders like {rank}
     * @param multiLine placeholders that expand into several lore lines when they are alone
     *                  on a line, like {requirements}
     */
    public ItemStack build(Player viewer, Map<String, String> placeholders, Map<String, List<String>> multiLine) {
        Material mat = material == null ? null : Material.matchMaterial(apply(material, placeholders));
        if (mat == null) {
            mat = Material.STONE;
        }
        ItemStack item = new ItemStack(mat, amount == null ? 1 : Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (name != null) {
            meta.setDisplayName(Text.color(viewer, "&r" + apply(name, placeholders)));
        }

        if (lore != null) {
            List<String> lines = new ArrayList<String>();
            for (String line : lore) {
                List<String> expanded = multiLine == null ? null : multiLine.get(line.trim());
                if (expanded != null) {
                    for (String e : expanded) {
                        lines.add(Text.color(viewer, "&r" + apply(e, placeholders)));
                    }
                } else {
                    lines.add(Text.color(viewer, "&r" + apply(line, placeholders)));
                }
            }
            meta.setLore(lines);
        }

        if (glow != null && glow) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
        }
        if (modelData != null) {
            meta.setCustomModelData(modelData);
        }
        if (meta instanceof SkullMeta && "viewer".equalsIgnoreCase(skull) && viewer != null) {
            ((SkullMeta) meta).setOwningPlayer(viewer);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static String apply(String text, Map<String, String> placeholders) {
        if (placeholders == null || text.indexOf('{') < 0) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            if (out.contains(e.getKey())) {
                out = out.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }
}
