package io.github.jiggy4413.prisoncore.util;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

/**
 * Turns mined drops into their smelted version (raw iron -> iron ingot, gold ore -> gold ingot...),
 * using the server's own furnace recipes. Shared by auto-smelt and the Explosive enchant.
 * What gets smelted is set in autominer.yml.
 */
public final class Smelting {

    private static Map<Material, ItemStack> furnace;
    private static final Set<Material> EXTRA = EnumSet.noneOf(Material.class);
    private static final Set<Material> EXCLUDE = EnumSet.noneOf(Material.class);
    private static boolean rawBlocks = true;

    private Smelting() {
    }

    /** Settings from autominer.yml. */
    public static void configure(List<String> extra, List<String> exclude, boolean smeltRawBlocks) {
        EXTRA.clear();
        EXCLUDE.clear();
        for (String s : extra) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                EXTRA.add(m);
            }
        }
        for (String s : exclude) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                EXCLUDE.add(m);
            }
        }
        rawBlocks = smeltRawBlocks;
    }

    /** The smelted version of a drop, or a copy of it unchanged if it doesn't smelt. */
    public static ItemStack smelt(ItemStack drop) {
        Material type = drop.getType();
        if (EXCLUDE.contains(type)) {
            return drop.clone();
        }
        if (rawBlocks) {
            Material block = rawBlock(type);
            if (block != null) {
                return new ItemStack(block, drop.getAmount());
            }
        }
        if (!isOre(type) && !EXTRA.contains(type)) {
            return drop.clone();
        }
        ItemStack result = recipes().get(type);
        if (result == null) {
            return drop.clone();
        }
        return new ItemStack(result.getType(), drop.getAmount() * Math.max(1, result.getAmount()));
    }

    private static boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS
                || type == Material.RAW_IRON || type == Material.RAW_GOLD || type == Material.RAW_COPPER;
    }

    /** Raw metal blocks have no furnace recipe; for a prison they smelt to the metal block. */
    private static Material rawBlock(Material type) {
        switch (type) {
            case RAW_IRON_BLOCK:
                return Material.IRON_BLOCK;
            case RAW_GOLD_BLOCK:
                return Material.GOLD_BLOCK;
            case RAW_COPPER_BLOCK:
                return Material.COPPER_BLOCK;
            default:
                return null;
        }
    }

    private static Map<Material, ItemStack> recipes() {
        if (furnace == null) {
            Map<Material, ItemStack> map = new EnumMap<Material, ItemStack>(Material.class);
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe r = it.next();
                if (!(r instanceof FurnaceRecipe)) {
                    continue;
                }
                FurnaceRecipe f = (FurnaceRecipe) r;
                RecipeChoice choice = f.getInputChoice();
                if (choice instanceof RecipeChoice.MaterialChoice) {
                    for (Material in : ((RecipeChoice.MaterialChoice) choice).getChoices()) {
                        if (!map.containsKey(in)) {
                            map.put(in, f.getResult());
                        }
                    }
                } else if (!map.containsKey(f.getInput().getType())) {
                    map.put(f.getInput().getType(), f.getResult());
                }
            }
            furnace = map;
        }
        return furnace;
    }
}
