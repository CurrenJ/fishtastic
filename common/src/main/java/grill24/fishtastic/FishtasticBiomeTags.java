package grill24.fishtastic;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Biome tags for Fishtastic mod
 */
public class FishtasticBiomeTags {
    /** Underground biomes whose water should always resolve to Zone.CAVE, regardless of Y level. */
    public static final TagKey<Biome> IS_CAVE_BIOME = create("is_cave_biome");

    private static TagKey<Biome> create(String name) {
        return TagKey.create(Registries.BIOME, Fishtastic.id(name));
    }
}
