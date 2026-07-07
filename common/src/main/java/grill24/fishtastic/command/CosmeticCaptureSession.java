package grill24.fishtastic.command;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player interactive state for the {@code /fishtastic cosmetic capture} wand workflow: tracks
 * which point (corner1, corner2, or anchor) the next wand right-click will set, and the points
 * picked so far. Started by {@code capture start}, driven by right-clicks with the capture wand
 * item, and consumed by {@code capture finish}.
 * <p>
 * Not cleaned up on player disconnect — same tradeoff as {@link grill24.fishtastic.block.FishTankEditModeManager},
 * a handful of leaked UUIDs for a debug-only tool is a non-issue.
 */
public final class CosmeticCaptureSession {

    public enum Mode {
        CORNER_1, CORNER_2, ANCHOR;

        public static final StreamCodec<ByteBuf, Mode> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(i -> Mode.values()[i], Enum::ordinal);
    }

    private static final Map<UUID, CosmeticCaptureSession> SESSIONS = new ConcurrentHashMap<>();

    private final ResourceKey<Level> dimension;
    private Mode mode = Mode.CORNER_1;
    @Nullable private BlockPos corner1;
    @Nullable private BlockPos corner2;
    @Nullable private BlockPos anchor;

    private CosmeticCaptureSession(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public static CosmeticCaptureSession start(UUID playerId, ResourceKey<Level> dimension) {
        CosmeticCaptureSession session = new CosmeticCaptureSession(dimension);
        SESSIONS.put(playerId, session);
        return session;
    }

    @Nullable
    public static CosmeticCaptureSession get(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static void cancel(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Mode mode() {
        return mode;
    }

    @Nullable
    public BlockPos corner1() {
        return corner1;
    }

    @Nullable
    public BlockPos corner2() {
        return corner2;
    }

    @Nullable
    public BlockPos anchor() {
        return anchor;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean isReadyToFinish() {
        return corner1 != null && corner2 != null && anchor != null;
    }

    /**
     * Records a wand click at {@code pos}. Corner picks auto-advance to the next mode
     * (corner1 -> corner2 -> anchor); anchor picks stay in anchor mode so repeated clicks keep
     * adjusting it. Returns a chat-ready description of what happened.
     */
    public String recordClick(BlockPos pos) {
        return switch (mode) {
            case CORNER_1 -> {
                corner1 = pos;
                mode = Mode.CORNER_2;
                yield "Corner 1 set at " + fmt(pos) + ". Right-click the second corner.";
            }
            case CORNER_2 -> {
                corner2 = pos;
                mode = Mode.ANCHOR;
                yield "Corner 2 set at " + fmt(pos) + ". Right-click the anchor block.";
            }
            case ANCHOR -> {
                anchor = pos;
                yield "Anchor set at " + fmt(pos) + ". Right-click again to adjust, or run "
                        + "/fishtastic cosmetic capture finish <name> [scale] to save.";
            }
        };
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
