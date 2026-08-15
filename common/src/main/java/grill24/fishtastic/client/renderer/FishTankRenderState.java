package grill24.fishtastic.client.renderer;

import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FishTankRenderState extends BlockEntityRenderState {
    /** A placed structure resolved to its actual definition, so rendering never does a registry
     * lookup on the render thread — resolution happens once in extractRenderState. */
    public record ResolvedStructureCosmetic(CosmeticStructure structure, Rotation rotation) {}

    public boolean hasOpenDownFace = false;
    /** Faces open to a connected neighbor tank (no glass there) — a copy of the block entity's set. */
    public Set<Direction> openFaces = Collections.emptySet();
    /** Body geometry this tank is currently using. */
    public FishTankShape shape = FishTankShape.STANDARD;
    /** Game time in ticks (gameTime + partialTick). */
    public float gameTimeTicks = 0f;
    /** Hash of this tank's block position, used to seed deterministic per-cell animations. */
    public int blockPosHash = 0;
    /** Cosmetic decorations placed in this tank's 3×3 floor grid. */
    public Map<CosmeticGridCell, PlacedCosmetic> cosmetics = Collections.emptyMap();
    /** Multi-block structure cosmetics, keyed by their anchor cell. */
    public Map<CosmeticGridCell, ResolvedStructureCosmetic> structureCosmetics = Collections.emptyMap();
    /**
     * Fish to render this frame. Contains one entry for solo fish, N entries for swarm species.
     * Back-to-front sorted by zOffset so depth ordering is correct.
     */
    public List<SwarmFishInstance> fishInstances = List.of();
    /**
     * Per-cell game time at which a chest cosmetic last released one bubble of its stream, used
     * to avoid releasing the same stream tick's bubble more than once when a frame is extracted
     * multiple times within the same game tick. Persists across frames — not reset in extractRenderState.
     */
    public final Map<CosmeticGridCell, Long> chestLastBubbleSpawnTick = new HashMap<>();
}
