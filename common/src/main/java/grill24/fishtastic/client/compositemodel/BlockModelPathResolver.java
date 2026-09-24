package grill24.fishtastic.client.compositemodel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves model locations for a block.
 *
 * <p>Returns the blockstate-redirect path first (if one was recorded by {@link BlockstateModelScanner}),
 * then the conventional {@code namespace:block/name} path as a fallback.
 *
 * <p>NeoForge extends this with config-driven overrides in its own {@code BlockModelPathResolver}.
 */
public class BlockModelPathResolver {

    public static List<ResourceLocation> getModelLocations(Block block) {
        List<ResourceLocation> locations = new ArrayList<>();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        ResourceLocation standardPath = blockId.withPrefix("block/");

        ResourceLocation redirect = BlockstateRedirectRegistry.getRedirect(standardPath);
        if (redirect != null) {
            locations.add(redirect);
        }

        locations.add(standardPath);
        return locations;
    }
}
