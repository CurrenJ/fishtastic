package grill24.fishtastic;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class FishtasticBlockTags {
    /** Vanilla blocks that can be placed as tank cosmetics using their regular item. */
    public static final TagKey<Block> TANK_COSMETICS = create("tank_cosmetics");

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, Fishtastic.id(name));
    }
}
