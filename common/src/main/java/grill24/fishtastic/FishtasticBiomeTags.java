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

    /**
     * Peak/alpine biomes whose water should always resolve to Zone.HIGH_ALTITUDE, regardless of Y
     * level — mirrors {@link #IS_CAVE_BIOME}. Reuses the existing {@code is_snowy_peaks} tag
     * (jagged_peaks, frozen_peaks, snowy_slopes, grove) already used as a soft biome_weight target
     * by arctic_char and friends, so a plateau on an unambiguous mountain biome always grants the
     * zone even if it happens to sit below the y+30 elevation band.
     */
    public static final TagKey<Biome> IS_SNOWY_PEAKS = create("is_snowy_peaks");

    private static TagKey<Biome> create(String name) {
        return TagKey.create(Registries.BIOME, Fishtastic.id(name));
    }
}
