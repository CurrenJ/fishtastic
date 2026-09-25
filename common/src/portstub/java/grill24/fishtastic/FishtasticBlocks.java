package grill24.fishtastic;

import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumMap;
import java.util.Map;

/**
 * Portstub (gradle/port-excludes.gradle): temporary stand-in for the real {@code FishtasticBlocks},
 * which pulls in the whole block/blockentity registration graph (B2.6/B5.3, not ported yet). Only
 * {@code CLEAR_STAINED_GLASS} is kept, for {@link grill24.fishtastic.component.FishTankMaterials#defaultMaterials()}.
 * Real file still exists (excluded) at FishtasticBlocks.java.
 */
public class FishtasticBlocks {
    public static final Map<DyeColor, Holder<Block>> CLEAR_STAINED_GLASS = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            CLEAR_STAINED_GLASS.put(color, Holder.direct(Blocks.GLASS));
        }
    }
}
