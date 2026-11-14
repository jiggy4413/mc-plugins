package io.github.jiggy4413.prisoncore.mines;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Refills one mine a slice at a time, bottom layer first, so no single tick does all the work.
 * Each call to {@link #step(long)} places blocks until its time budget runs out.
 */
final class ResetJob {

    final Mine mine;
    private final World world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Composition composition;
    private final Material surface;
    private final boolean fillMode;
    private final long total;

    private int x, y, z;
    private long processed;
    private long airAfter;

    ResetJob(Mine mine, World world) {
        this.mine = mine;
        this.world = world;
        this.minX = mine.minX;
        this.minY = mine.minY;
        this.minZ = mine.minZ;
        this.maxX = mine.maxX;
        this.maxY = mine.maxY;
        this.maxZ = mine.maxZ;
        this.composition = mine.composition;
        this.surface = mine.surface;
        this.fillMode = mine.fillMode;
        this.total = mine.volume();
        this.x = minX;
        this.y = minY;
        this.z = minZ;
    }

    double progress() {
        return total <= 0 ? 1 : Math.min(1, (double) processed / total);
    }

    long airAfter() {
        return airAfter;
    }

    /** Places blocks until System.nanoTime() passes the deadline. Returns true when the mine is full. */
    boolean step(long deadlineNanos) {
        while (y <= maxY) {
            Block block = world.getBlockAt(x, y, z);
            Material current = block.getType();
            Material target;
            if (fillMode && !current.isAir()) {
                target = current;                    // fill mode keeps blocks that are still there
            } else {
                target = (surface != null && y == maxY) ? surface : composition.pick();
                if (current != target) {
                    block.setType(target, false);    // no physics: sand and gravel stay put
                }
            }
            if (target.isAir()) {
                airAfter++;
            }
            processed++;

            // advance: x, then z, then the next layer up
            if (++x > maxX) {
                x = minX;
                if (++z > maxZ) {
                    z = minZ;
                    y++;
                }
            }
            if ((processed & 255) == 0 && System.nanoTime() > deadlineNanos) {
                return y > maxY;
            }
        }
        return true;
    }
}
