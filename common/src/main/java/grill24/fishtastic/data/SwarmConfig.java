package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Configures multi-fish swarm display for a species. When present on a FishProfile, the tank
 * renderer will pull from the first {@link #count} occupied slots and distribute them spatially
 * across depth layers, rather than showing only one fish.
 *
 * <p>{@link #count} is purely a render-side draw-count ceiling; it does not gate how many fish a
 * tank can actually hold — that's {@link TankCapacity}'s size-based budget. The default of 25
 * matches the most fish TankCapacity's budget can ever admit (a full tank of minimum-cost tiny
 * fish), so it never clips a legitimately-admitted shoal. Species that want a tighter artistic
 * cap regardless of size can still override {@code count} downward in their fish_profile JSON.
 */
public record SwarmConfig(
        int count,
        int depthLayers,
        float xzSpread,
        float yRange,
        float rotationJitter
) {
    public static final SwarmConfig DEFAULT = new SwarmConfig(25, 3, 0.22f, 0.25f, 0f);

    public static final Codec<SwarmConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("count", 25).forGetter(SwarmConfig::count),
            Codec.INT.optionalFieldOf("depth_layers", 3).forGetter(SwarmConfig::depthLayers),
            Codec.FLOAT.optionalFieldOf("xz_spread", 0.22f).forGetter(SwarmConfig::xzSpread),
            Codec.FLOAT.optionalFieldOf("y_range", 0.25f).forGetter(SwarmConfig::yRange),
            Codec.FLOAT.optionalFieldOf("rotation_jitter", 0f).forGetter(SwarmConfig::rotationJitter)
    ).apply(i, SwarmConfig::new));

    /**
     * Looks up the swarm config for the given item's fish profile, or {@link #DEFAULT} if it has
     * none. Shared by the tank renderer (to decide how many fish to draw) and
     * {@code FishTankBlockEntity} (to cap insertion at that same count) so the two never disagree.
     */
    public static SwarmConfig resolve(ItemStack stack, Level level) {
        if (stack.isEmpty()) return DEFAULT;

        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return DEFAULT;

        ResourceKey<FishProfile> profileKey = ResourceKey.create(
                FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());

        return level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey)
                .map(FishProfile::swarm)
                .orElse(DEFAULT);
    }
}
