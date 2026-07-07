package grill24.fishtastic.client;

import grill24.fishtastic.command.CosmeticCaptureSession;
import grill24.fishtastic.network.CosmeticCaptureSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side mirror of the local player's {@link CosmeticCaptureSession}, updated by
 * {@link CosmeticCaptureSyncPacket}. Draws a gizmo preview of the current selection every client
 * tick — see {@code onClientTick}/{@code END_CLIENT_TICK} in the Fabric/NeoForge client entry
 * points, which run inside Minecraft's per-tick gizmo collection scope.
 */
public final class CosmeticCaptureClientState {

    private static final int CORNER_COLOR = ARGB.color(255, 255, 215, 0);
    private static final int ANCHOR_COLOR = ARGB.color(255, 255, 0, 255);

    private static boolean active = false;
    private static CosmeticCaptureSession.Mode mode = CosmeticCaptureSession.Mode.CORNER_1;
    @Nullable private static BlockPos corner1;
    @Nullable private static BlockPos corner2;
    @Nullable private static BlockPos anchor;

    private CosmeticCaptureClientState() {
    }

    public static void apply(CosmeticCaptureSyncPacket packet) {
        active = packet.active();
        mode = packet.mode();
        corner1 = packet.corner1().orElse(null);
        corner2 = packet.corner2().orElse(null);
        anchor = packet.anchor().orElse(null);
    }

    public static void reset() {
        active = false;
        corner1 = null;
        corner2 = null;
        anchor = null;
    }

    /** Must be called from within Minecraft's per-tick gizmo collection scope (client tick hook). */
    public static void tickGizmos() {
        if (!active) return;

        if (corner1 != null && corner2 != null) {
            Gizmos.cuboid(boxOf(corner1, corner2), GizmoStyle.stroke(CORNER_COLOR, 2f));
        } else if (corner1 != null) {
            Gizmos.cuboid(corner1, GizmoStyle.stroke(CORNER_COLOR, 2f));
        }

        if (anchor != null) {
            Gizmos.cuboid(anchor, GizmoStyle.strokeAndFill(ANCHOR_COLOR, 2f, ARGB.color(80, 255, 0, 255)));
        }
    }

    private static AABB boxOf(BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }
}
