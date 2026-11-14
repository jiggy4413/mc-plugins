package io.github.jiggy4413.prisoncore.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BossBar;

/** One mine: its settings from mines.yml plus live state (how much is mined, reset status). */
public final class Mine {

    // ---------------------------------------------------------------- settings
    final String id;
    String display;
    String worldName;
    boolean hasRegion;
    int minX, minY, minZ, maxX, maxY, maxZ;
    /** Where players are sent when the mine resets. Null = top middle of the mine. */
    double[] spawn;
    double resetAtPercent;
    int timerMinutes;
    double timerMinimumMined;
    boolean fillMode;
    Material surface;
    Composition composition = new Composition();

    // ---------------------------------------------------------------- live state
    /** Air blocks in the region right now; -1 until the first count finishes. */
    long airNow = -1;
    /** Air blocks right after a reset (from AIR in the block mix). */
    long airBaseline = 0;
    /** Block changes seen by events while a background count is running. */
    long pendingDelta;
    /** Bumped when a reset starts, so an older background count is thrown away. */
    int countGeneration;
    boolean counting;
    long lastResetMillis = System.currentTimeMillis();
    long lastCountMillis;
    boolean queued;
    boolean resetting;
    double resetProgress;
    /** Seconds left in the reset countdown; -1 when none is running. */
    int warningLeft = -1;
    BossBar bossBar;

    Mine(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display == null || display.isEmpty() ? id.toUpperCase() : display;
    }

    public World world() {
        return worldName == null || worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
    }

    public long volume() {
        if (!hasRegion) {
            return 0;
        }
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean isCounted() {
        return airNow >= 0;
    }

    public boolean isResetting() {
        return resetting;
    }

    public boolean isWarning() {
        return warningLeft >= 0;
    }

    public double resetAtPercent() {
        return resetAtPercent;
    }

    /** 0..100: how much of the mine has been mined since it was last full. */
    public double minedPercent() {
        if (!hasRegion || airNow < 0) {
            return 0;
        }
        long solid = volume() - airBaseline;
        if (solid <= 0) {
            return 0;
        }
        double p = (airNow - airBaseline) * 100.0 / solid;
        return Math.max(0, Math.min(100, p));
    }

    /** Mined percent with one decimal, e.g. "12.4", so big mines visibly move. */
    public String minedText() {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.floor(minedPercent() * 10) / 10.0);
    }

    /** Milliseconds until the timer resets the mine, or -1 when the timer is off. */
    public long millisUntilTimer() {
        if (timerMinutes <= 0) {
            return -1;
        }
        long due = lastResetMillis + timerMinutes * 60000L;
        return Math.max(0, due - System.currentTimeMillis());
    }

    public boolean contains(World world, int x, int y, int z) {
        return hasRegion && world != null && world.getName().equals(worldName)
                && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location loc) {
        return loc != null && contains(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** The mine plus a margin around it, used for the boss bar and messages. */
    public boolean isNear(Location loc, int range) {
        if (!hasRegion || loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX - range && x <= maxX + range
                && z >= minZ - range && z <= maxZ + range
                && y >= minY - range && y <= maxY + range + 10;
    }

    /** Players inside this column (from the bottom of the mine to just above its top) get moved out on reset. */
    public boolean isInside(Location loc) {
        if (!hasRegion || loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY - 1 && y <= maxY + 2;
    }

    public Location safeLocation() {
        World w = world();
        if (w == null) {
            return null;
        }
        if (spawn != null) {
            return new Location(w, spawn[0], spawn[1], spawn[2], (float) spawn[3], (float) spawn[4]);
        }
        return new Location(w, (minX + maxX + 1) / 2.0, maxY + 1, (minZ + maxZ + 1) / 2.0);
    }

    /** Standing on top of the (full) mine, in the middle. */
    public Location topLocation() {
        World w = world();
        return w == null ? null : new Location(w, (minX + maxX + 1) / 2.0, maxY + 1, (minZ + maxZ + 1) / 2.0);
    }

    String regionString() {
        return minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
    }

    void setRegion(int x1, int y1, int z1, int x2, int y2, int z2) {
        minX = Math.min(x1, x2);
        minY = Math.max(0, Math.min(y1, y2));        // 1.16 worlds are 0-255 tall
        minZ = Math.min(z1, z2);
        maxX = Math.max(x1, x2);
        maxY = Math.min(255, Math.max(y1, y2));
        maxZ = Math.max(z1, z2);
        hasRegion = maxY >= minY;   // a region entirely outside 0-255 isn't usable
    }

    boolean sameRegionAs(Mine other) {
        return hasRegion && other.hasRegion && worldName != null && worldName.equals(other.worldName)
                && minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
    }
}
