package grill24.fishtastic.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * An item that can be placed as a cosmetic decoration inside a fish tank.
 * The renderBlock is the vanilla block whose model is rendered at the placement position.
 */
public class FishTankCosmeticItem extends Item {

    private static final Map<Block, FishTankCosmeticItem> BY_BLOCK = new HashMap<>();

    private final Block renderBlock;

    public FishTankCosmeticItem(Block renderBlock, Properties properties) {
        super(properties);
        this.renderBlock = renderBlock;
        BY_BLOCK.put(renderBlock, this);
    }

    public Block getRenderBlock() {
        return renderBlock;
    }

    @Nullable
    public static FishTankCosmeticItem forBlock(Block block) {
        return BY_BLOCK.get(block);
    }
}
