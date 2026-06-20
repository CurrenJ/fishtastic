package grill24.fishtastic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Snapshot of the shared "Clean Up the Waters" goal, synced alongside per-player quest state.
 * milestoneReached is non-zero only on the packet that announces a newly crossed threshold —
 * the client uses it to fire a one-off banner notification rather than a persistent value.
 */
public record CleanupGoalProgress(int total, int threshold, int milestoneReached) {
    public static final CleanupGoalProgress EMPTY = new CleanupGoalProgress(0, 0, 0);

    public static final StreamCodec<ByteBuf, CleanupGoalProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CleanupGoalProgress::total,
            ByteBufCodecs.VAR_INT, CleanupGoalProgress::threshold,
            ByteBufCodecs.VAR_INT, CleanupGoalProgress::milestoneReached,
            CleanupGoalProgress::new
    );
}
