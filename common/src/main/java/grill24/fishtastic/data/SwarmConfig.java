package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Configures multi-fish swarm display for a species. When present on a FishProfile, the tank
 * renderer will pull from the first {@link #count} occupied slots and distribute them spatially
 * across depth layers, rather than showing only one fish.
 */
public record SwarmConfig(
        int count,
        int depthLayers,
        float xzSpread,
        float yRange,
        float rotationJitter
) {
    public static final Codec<SwarmConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("count", 5).forGetter(SwarmConfig::count),
            Codec.INT.optionalFieldOf("depth_layers", 3).forGetter(SwarmConfig::depthLayers),
            Codec.FLOAT.optionalFieldOf("xz_spread", 0.22f).forGetter(SwarmConfig::xzSpread),
            Codec.FLOAT.optionalFieldOf("y_range", 0.25f).forGetter(SwarmConfig::yRange),
            Codec.FLOAT.optionalFieldOf("rotation_jitter", 180f).forGetter(SwarmConfig::rotationJitter)
    ).apply(i, SwarmConfig::new));
}
