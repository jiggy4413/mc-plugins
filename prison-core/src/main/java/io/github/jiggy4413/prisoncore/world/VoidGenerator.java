package io.github.jiggy4413.prisoncore.world;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

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

    /** the_void biome everywhere: no mob spawning, no rain or snow */
    private static final BiomeProvider VOID_BIOME = new BiomeProvider() {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return Collections.singletonList(Biome.THE_VOID);
        }
    };

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return VOID_BIOME;
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        if (chunkX == 0 && chunkZ == 0) {
            data.setBlock(0, SPAWN_Y - 1, 0, Material.BEDROCK);
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
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
