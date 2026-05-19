package grill24.fishtastic.fabric.fishtank;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Resolves model locations for blocks.
 * Simplified version of the NeoForge BlockModelPathResolver — returns the standard
 * block model path without config-based overrides (no config system on Fabric yet).
 */
public class BlockModelPathResolverFabric {

    public static List<Identifier> getModelLocations(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        return List.of(blockId.withPrefix("block/"));
    }
}
