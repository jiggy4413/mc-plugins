package io.github.jiggy4413.prisoncore.mines;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads MineResetLite's saved mines (plugins/MineResetLite/mines/*.mine.yml) and copies their
 * region, block mix and settings into PrisonCore. Works whether or not MineResetLite is still
 * installed, and with the original plugin and its 1.13+ forks.
 */
final class MrlImporter {

    static final class Result {
        final List<String> imported = new ArrayList<String>();
        final List<String> skipped = new ArrayList<String>();
        final List<String> problems = new ArrayList<String>();
    }

    private MrlImporter() {
    }

    static Result run(MineManager manager, File pluginsFolder, boolean overwrite) {
        Result result = new Result();
        File folder = new File(new File(pluginsFolder, "MineResetLite"), "mines");
        File[] files = folder.listFiles();
        if (files == null) {
            result.problems.add("No folder plugins/MineResetLite/mines found.");
            return result;
        }
        for (File f : files) {
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                continue;
            }
            try {
                importFile(manager, f, overwrite, result);
            } catch (Exception e) {
                result.problems.add(f.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void importFile(MineManager manager, File f, boolean overwrite, Result result) throws Exception {
        Object root;
        Reader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
        try {
            // plain YAML read: MineResetLite's "==" class marker is just treated as a key
            root = new Yaml(new SafeConstructor()).load(reader);
        } finally {
            reader.close();
        }
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("not a mine file");
        }
        Object mineObj = ((Map<String, Object>) root).get("mine");
        Map<String, Object> me = mineObj instanceof Map ? (Map<String, Object>) mineObj : (Map<String, Object>) root;

        String name = str(me.get("name"), f.getName().replaceAll("(?i)\\.mine\\.yml$|\\.yml$", ""));
        String id = normalizeId(name);
        Mine existing = manager.get(id);
        if (existing != null && existing.hasRegion && !overwrite) {
            result.skipped.add(name + " (already set up as " + id + ")");
            return;
        }

        int[] region = new int[]{
                num(me.get("minX")), num(me.get("minY")), num(me.get("minZ")),
                num(me.get("maxX")), num(me.get("maxY")), num(me.get("maxZ"))};
        String world = str(me.get("world"), "");
        if (world.isEmpty()) {
            throw new IllegalArgumentException("no world");
        }

        Map<Material, Double> blocks = new LinkedHashMap<Material, Double>();
        double sum = 0;
        Object comp = me.get("composition");
        if (comp instanceof Map) {
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) comp).entrySet()) {
                Material mat = parseMrlBlock(String.valueOf(e.getKey()));
                double share = e.getValue() instanceof Number ? ((Number) e.getValue()).doubleValue() : 0;
                if (mat == null) {
                    result.problems.add(name + ": unknown block '" + e.getKey() + "', skipped");
                    continue;
                }
                double pct = share * 100.0;   // MineResetLite stores 0.6 for 60%
                Double prev = blocks.get(mat);
                blocks.put(mat, (prev == null ? 0 : prev) + pct);
                sum += pct;
            }
        }
        // MineResetLite fills whatever is left over with air
        if (sum > 0 && sum < 99.5) {
            Double prev = blocks.get(Material.AIR);
            blocks.put(Material.AIR, (prev == null ? 0 : prev) + (100 - sum));
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("no blocks in its composition");
        }

        boolean fill = me.get("fillMode") instanceof Boolean && (Boolean) me.get("fillMode");
        Material surface = null;
        Object s = me.get("surface");
        if (s != null && !String.valueOf(s).isEmpty()) {
            surface = parseMrlBlock(String.valueOf(s));
        }
        double[] spawn = null;
        if (me.get("tpY") instanceof Number && ((Number) me.get("tpY")).doubleValue() > 0) {
            spawn = new double[]{dbl(me.get("tpX")), dbl(me.get("tpY")), dbl(me.get("tpZ")),
                    dbl(me.get("tpYaw")), dbl(me.get("tpPitch"))};
        }
        Double resetPercent = null;
        if (me.get("resetPercent") instanceof Number) {
            double rp = ((Number) me.get("resetPercent")).doubleValue();
            // some forks store a fraction (0.75), some a percent (75); only positive values mean "on"
            if (rp > 0) {
                resetPercent = rp <= 1 ? rp * 100 : rp;
            }
        }
        Integer timer = me.get("resetDelay") instanceof Number ? ((Number) me.get("resetDelay")).intValue() : null;
        manager.importMine(id, world, region, blocks, fill, surface, spawn, resetPercent, timer);
        result.imported.add(name + " -> " + id);
    }

    /** "A", "mineA", "Mine A" all become "a"; anything else is lowercased without spaces. */
    static String normalizeId(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "").replace(".", "");
        if (n.startsWith("mine") && n.length() > 4) {
            n = n.substring(4);
        }
        return n.isEmpty() ? name.toLowerCase(Locale.ROOT) : n;
    }

    /** Handles STONE, minecraft:stone, minecraft:stone[...], STONE:0 and old numeric ids like 1:0. */
    @SuppressWarnings("deprecation")
    static Material parseMrlBlock(String key) {
        String k = key.trim();
        if (k.matches("\\d+(:\\d+)?")) {
            String[] bits = k.split(":");
            int id = Integer.parseInt(bits[0]);
            byte data = bits.length > 1 ? Byte.parseByte(bits[1]) : 0;
            for (Material legacy : Material.values()) {
                if (legacy.isLegacy() && legacy.getId() == id) {
                    try {
                        Material modern = Bukkit.getUnsafe().fromLegacy(legacy, data).getMaterial();
                        return modern.isBlock() ? modern : null;
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
            }
            return null;
        }
        if (k.matches("[A-Za-z_]+:\\d+")) {
            k = k.substring(0, k.indexOf(':'));   // "STONE:0"
        }
        return MineManager.parseBlock(k);
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static int num(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        throw new IllegalArgumentException("missing corner coordinates");
    }

    private static double dbl(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }
}
