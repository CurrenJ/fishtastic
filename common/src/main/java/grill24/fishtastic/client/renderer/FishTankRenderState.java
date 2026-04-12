package grill24.fishtastic.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class FishTankRenderState extends BlockEntityRenderState {
    public ItemStack itemToRender = ItemStack.EMPTY;
    public float firstItemRotation = 0f;
    public boolean hasOpenDownFace = false;
    /** Game time in ticks (gameTime + partialTick). */
    public float gameTimeTicks = 0f;
    /** Block position hash used as per-tank random seed for animation. */
    public int blockPosHash = 0;
}
