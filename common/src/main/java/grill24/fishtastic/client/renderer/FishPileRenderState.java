package grill24.fishtastic.client.renderer;

import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/** One frame's snapshot of a pile (no longer a vanilla render state on 1.21.1; see {@link FishTankRenderState}). */
public class FishPileRenderState {
    /** Packed block/sky light for this pile (26.1's {@code BlockEntityRenderState.lightCoords}). */
    public int lightCoords;
    /** Snapshot of the piled fish, in insertion order (oldest first). */
    public List<ItemStack> fish = Collections.emptyList();
    /** Hash of this pile's block position, used to seed deterministic per-fish jitter/rotation. */
    public int blockPosHash = 0;
}
