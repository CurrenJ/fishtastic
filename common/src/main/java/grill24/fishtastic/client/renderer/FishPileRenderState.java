package grill24.fishtastic.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class FishPileRenderState extends BlockEntityRenderState {
    /** Snapshot of the piled fish, in insertion order (oldest first). */
    public List<ItemStack> fish = Collections.emptyList();
    /** Hash of this pile's block position, used to seed deterministic per-fish jitter/rotation. */
    public int blockPosHash = 0;
}
