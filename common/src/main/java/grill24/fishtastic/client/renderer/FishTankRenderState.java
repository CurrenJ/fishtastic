package grill24.fishtastic.client.renderer;

import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;

public class FishTankRenderState extends BlockEntityRenderState {
    public ItemStack itemToRender = ItemStack.EMPTY;
    public float firstItemRotation = 0f;
    public boolean hasOpenDownFace = false;
    /** Game time in ticks (gameTime + partialTick). */
    public float gameTimeTicks = 0f;
    /** Block position hash used as per-tank random seed for animation. */
    public int blockPosHash = 0;
    /** Cosmetic decorations placed in this tank's 3×3 floor grid. */
    public Map<CosmeticGridCell, PlacedCosmetic> cosmetics = Collections.emptyMap();
    /** Animation config resolved from the displayed item's fish profile. */
    public FishAnimationConfig animationConfig = FishAnimationConfig.HorizontalSwim.DEFAULT;
}
