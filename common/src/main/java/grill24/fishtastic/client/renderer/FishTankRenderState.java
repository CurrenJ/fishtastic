package grill24.fishtastic.client.renderer;

import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FishTankRenderState extends BlockEntityRenderState {
    public boolean hasOpenDownFace = false;
    /** Game time in ticks (gameTime + partialTick). */
    public float gameTimeTicks = 0f;
    /** Cosmetic decorations placed in this tank's 3×3 floor grid. */
    public Map<CosmeticGridCell, PlacedCosmetic> cosmetics = Collections.emptyMap();
    /**
     * Fish to render this frame. Contains one entry for solo fish, N entries for swarm species.
     * Back-to-front sorted by zOffset so depth ordering is correct.
     */
    public List<SwarmFishInstance> fishInstances = List.of();
}
