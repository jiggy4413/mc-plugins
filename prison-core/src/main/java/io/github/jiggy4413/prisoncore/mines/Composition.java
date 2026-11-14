package io.github.jiggy4413.prisoncore.mines;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;

/** The block mix of a mine: materials with relative weights (they don't have to add up to 100). */
public final class Composition {

    private final List<Material> materials = new ArrayList<Material>();
    private final List<Double> cumulative = new ArrayList<Double>();
    private final Map<Material, Double> weights = new LinkedHashMap<Material, Double>();
    private double total;

    public void add(Material material, double weight) {
        if (material == null || weight <= 0) {
            return;
        }
        Double existing = weights.get(material);
        weights.put(material, (existing == null ? 0 : existing) + weight);
        rebuild();
    }

    private void rebuild() {
        materials.clear();
        cumulative.clear();
        total = 0;
        for (Map.Entry<Material, Double> e : weights.entrySet()) {
            total += e.getValue();
            materials.add(e.getKey());
            cumulative.add(total);
        }
    }

    public boolean isEmpty() {
        return materials.isEmpty();
    }

    public Map<Material, Double> weights() {
        return Collections.unmodifiableMap(weights);
    }

    /** Share of the mix that is air (0..1), used as the "freshly reset" baseline. */
    public double airShare() {
        if (total <= 0) {
            return 0;
        }
        double air = 0;
        for (Map.Entry<Material, Double> e : weights.entrySet()) {
            if (e.getKey().isAir()) {
                air += e.getValue();
            }
        }
        return air / total;
    }

    /** Picks a random material by weight. Never call on an empty composition. */
    public Material pick() {
        if (materials.size() == 1) {
            return materials.get(0);
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < cumulative.size(); i++) {
            if (r < cumulative.get(i)) {
                return materials.get(i);
            }
        }
        return materials.get(materials.size() - 1);
    }
}
