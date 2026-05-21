package grill24.fishtastic.client.compositemodel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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

    public static List<Identifier> getModelLocations(Block block) {
        List<Identifier> locations = new ArrayList<>();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        Identifier standardPath = blockId.withPrefix("block/");

        Identifier redirect = BlockstateRedirectRegistry.getRedirect(standardPath);
        if (redirect != null) {
            locations.add(redirect);
        }

        locations.add(standardPath);
        return locations;
    }
}
