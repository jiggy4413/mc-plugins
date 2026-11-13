package io.github.jiggy4413.prisoncore.world;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

/**
 * Generates completely empty chunks, with one bedrock block under the spawn point so players
 * arriving in a new void world don't fall.
 *
 * Use it with Multiverse:  /mv create <name> normal -g PrisonCore
 *
 * Kept self-contained (no other PrisonCore classes) so a world keeps generating even after a
 * live PrisonCore update has swapped the plugin's code.
 */
public final class VoidGenerator extends ChunkGenerator {

    private static final int SPAWN_Y = 64;

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);

        // the_void biome: no mob spawning, no rain or snow
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int y = 0; y < world.getMaxHeight(); y += 4) {
                    biome.setBiome(x, y, z, Biome.THE_VOID);
                }
            }
        }

        if (chunkX == 0 && chunkZ == 0) {
            data.setBlock(0, SPAWN_Y - 1, 0, Material.BEDROCK);
        }
        return data;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, SPAWN_Y, 0.5);
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }
}
