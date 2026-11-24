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
 * Fields: material, name, lore, glow, model-data, amount, color (#RRGGBB, leather), unbreakable,
 * skull ("viewer" = the player's head),
 * texture (a head texture: Minecraft-Heads "Value", a textures.minecraft.net URL, or its id).
 */
public final class ItemTemplate {

    private String material;
    private String name;
    private List<String> lore;
    private Boolean glow;
    private Integer modelData;
    private Integer amount;
    private String skull;
    private String texture;
    /** Dye color for leather items, as #RRGGBB. */
    private String color;
    private Boolean unbreakable;
    /** Exact stats in the main hand, as shown in-game (e.g. 9 damage, 1.2 attacks per second). */
    private Double attackDamage;
    private Double attackSpeed;

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
            if (t.texture == null && s.isString("texture")) {
                t.texture = s.getString("texture");
            }
            if (t.color == null && s.isString("color")) {
                t.color = s.getString("color");
            }
            if (t.unbreakable == null && s.isBoolean("unbreakable")) {
                t.unbreakable = s.getBoolean("unbreakable");
            }
            if (t.attackDamage == null && (s.isDouble("attack-damage") || s.isInt("attack-damage"))) {
                t.attackDamage = s.getDouble("attack-damage");
            }
            if (t.attackSpeed == null && (s.isDouble("attack-speed") || s.isInt("attack-speed"))) {
                t.attackSpeed = s.getDouble("attack-speed");
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
        if (color != null && meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta) {
            String hex = apply(color, placeholders).trim().replace("#", "");
            try {
                ((org.bukkit.inventory.meta.LeatherArmorMeta) meta).setColor(
                        org.bukkit.Color.fromRGB(Integer.parseInt(hex, 16) & 0xFFFFFF));
            } catch (NumberFormatException ignored) {
                // not a color; keep the default leather color
            }
        }
        if (unbreakable != null && unbreakable) {
            meta.setUnbreakable(true);
        }
        // players have 1 base damage and 4 base attack speed; the modifiers add the rest
        if (attackDamage != null) {
            meta.removeAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
                    new org.bukkit.attribute.AttributeModifier(java.util.UUID.nameUUIDFromBytes("pc_damage".getBytes()),
                            "prisoncore.damage", attackDamage - 1, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlot.HAND));
        }
        if (attackSpeed != null) {
            meta.removeAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED);
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED,
                    new org.bukkit.attribute.AttributeModifier(java.util.UUID.nameUUIDFromBytes("pc_speed".getBytes()),
                            "prisoncore.speed", attackSpeed - 4, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlot.HAND));
        }
        if (meta instanceof SkullMeta && "viewer".equalsIgnoreCase(skull) && viewer != null) {
            ((SkullMeta) meta).setOwningPlayer(viewer);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        if (texture != null && !texture.isEmpty()) {
            applyTexture(item, apply(texture, placeholders));
        }
        return item;
    }

    /**
     * Gives a player head a custom texture. Accepts a Minecraft-Heads "Value" (base64),
     * a textures.minecraft.net URL, or just the id at the end of that URL.
     * Does nothing for items that aren't player heads or when the texture is blank.
     */
    public static void applyTexture(ItemStack item, String texture) {
        if (item == null || texture == null || texture.trim().isEmpty()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof SkullMeta)) {
            return;
        }
        String t = texture.trim();
        String value;
        if (t.startsWith("eyJ")) {
            value = t;
        } else {
            String url = t.startsWith("http") ? t : "http://textures.minecraft.net/texture/" + t;
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            value = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            // same texture → same id, so the client caches it instead of reloading every refresh
            java.util.UUID id = java.util.UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            com.destroystokyo.paper.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(id, "PrisonCore");
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", value));
            ((SkullMeta) meta).setPlayerProfile(profile);
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
            // not on Paper, or a bad texture: keep the plain head
        }
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
