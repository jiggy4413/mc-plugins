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
 * item-model (resource pack model id, e.g. classicprison:thornheart_chestplate),
 * equipment (how worn armor looks: a resource pack equipment id like classicprison:thornheart,
 * or "model" to wear the item's own 3D model, used for 3D helmets),
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
    /** Armor stats when worn (e.g. 8 armor on a chestplate fills 4 icons of the armor bar). */
    private Double armor;
    private Double armorToughness;
    private Double knockbackResistance;
    /** Resource pack item model (1.21.4 item_model component). */
    private String itemModel;
    /** Worn look: an equipment asset id, or "model" to show the item's own model when worn. */
    private String equipment;

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
            if (t.armor == null && (s.isDouble("armor") || s.isInt("armor"))) {
                t.armor = s.getDouble("armor");
            }
            if (t.armorToughness == null && (s.isDouble("armor-toughness") || s.isInt("armor-toughness"))) {
                t.armorToughness = s.getDouble("armor-toughness");
            }
            if (t.knockbackResistance == null && (s.isDouble("knockback-resistance") || s.isInt("knockback-resistance"))) {
                t.knockbackResistance = s.getDouble("knockback-resistance");
            }
            if (t.itemModel == null && s.isString("item-model")) {
                t.itemModel = s.getString("item-model");
            }
            if (t.equipment == null && s.isString("equipment")) {
                t.equipment = s.getString("equipment");
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
            meta.setEnchantmentGlintOverride(true);
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
            meta.removeAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                    new org.bukkit.attribute.AttributeModifier(new org.bukkit.NamespacedKey("prisoncore", "damage"),
                            attackDamage - 1, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
        if (attackSpeed != null) {
            meta.removeAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                    new org.bukkit.attribute.AttributeModifier(new org.bukkit.NamespacedKey("prisoncore", "speed"),
                            attackSpeed - 4, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
        // armor stats replace the item's normal ones (Minecraft drops an item's default armor once it has
        // its own), so leather can show a full armor bar
        if (armor != null || armorToughness != null || knockbackResistance != null) {
            org.bukkit.inventory.EquipmentSlot slot = wornSlot(mat);
            armorStat(meta, org.bukkit.attribute.Attribute.ARMOR, "armor", armor, slot);
            armorStat(meta, org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS, "toughness", armorToughness, slot);
            armorStat(meta, org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, "knockback", knockbackResistance, slot);
        }
        if (itemModel != null) {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(apply(itemModel, placeholders).trim().toLowerCase());
            if (key != null) {
                meta.setItemModel(key);
            }
        }
        if (equipment != null) {
            org.bukkit.inventory.EquipmentSlot slot = wornSlot(mat);
            if (slot != null) {
                org.bukkit.inventory.meta.components.EquippableComponent eq = meta.getEquippable();
                eq.setSlot(slot);
                String e = apply(equipment, placeholders).trim().toLowerCase();
                // "model": no armor layer, so the game draws the item's own (3D) model on the head
                eq.setModel(e.equals("model") ? null : org.bukkit.NamespacedKey.fromString(e));
                if (eq.getEquipSound() == null) {
                    eq.setEquipSound(org.bukkit.Sound.ITEM_ARMOR_EQUIP_NETHERITE);
                }
                meta.setEquippable(eq);
            }
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

    private static org.bukkit.inventory.EquipmentSlot wornSlot(Material mat) {
        String n = mat.name();
        if (n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) {
            return org.bukkit.inventory.EquipmentSlot.CHEST;
        }
        if (n.endsWith("_LEGGINGS")) {
            return org.bukkit.inventory.EquipmentSlot.LEGS;
        }
        if (n.endsWith("_BOOTS")) {
            return org.bukkit.inventory.EquipmentSlot.FEET;
        }
        return org.bukkit.inventory.EquipmentSlot.HEAD; // helmets, heads, pumpkins...
    }

    private static void armorStat(ItemMeta meta, org.bukkit.attribute.Attribute attribute, String id, Double value,
                                  org.bukkit.inventory.EquipmentSlot slot) {
        if (value == null) {
            return;
        }
        meta.removeAttributeModifier(attribute);
        meta.addAttributeModifier(attribute, new org.bukkit.attribute.AttributeModifier(
                new org.bukkit.NamespacedKey("prisoncore", id + "_" + slot.name().toLowerCase(java.util.Locale.ROOT)),
                value, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, slot.getGroup()));
    }
}
