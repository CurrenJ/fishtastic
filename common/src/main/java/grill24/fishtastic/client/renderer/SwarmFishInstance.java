package grill24.fishtastic.client.renderer;

import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.world.item.ItemStack;

/**
 * Precomputed per-fish data for a single member of a swarm, built during extractRenderState and
 * consumed during submit. Positions are offsets from the tank's XZ centre and animation base-Y.
 */
public record SwarmFishInstance(
        ItemStack stack,
        FishAnimationConfig animationConfig,
        float renderCalibration,
        float baseRotation,
        float xOffset,
        float yOffset,
        float zOffset,
        long seed,
        boolean mirrored
) {}
