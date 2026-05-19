package grill24.fishtastic.fabric.fishtank;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Resolves model locations for blocks.
 * Checks {@link BlockstateRedirectRegistry} (populated by {@link BlockstateModelRedirectPlugin}
 * before baking begins) so that blocks whose blockstate JSON points to a non-standard model path
 * (e.g. {@code block/glass/borderless_glass} instead of {@code block/borderless_glass}) are
 * found correctly.
 */
public class BlockModelPathResolverFabric {

    public static List<Identifier> getModelLocations(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        Identifier standardPath = blockId.withPrefix("block/");

        Identifier redirect = BlockstateRedirectRegistry.getRedirect(standardPath);
        if (redirect != null) {
            return List.of(redirect, standardPath);
        }
        return List.of(standardPath);
    }
}
