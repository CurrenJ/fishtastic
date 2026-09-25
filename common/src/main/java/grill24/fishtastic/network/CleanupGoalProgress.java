package grill24.fishtastic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot of the shared "Clean Up the Waters" goal, synced alongside per-player quest state.
 * milestoneReached is non-zero only on the packet that announces a newly crossed threshold —
 * the client uses it to fire a one-off banner notification rather than a persistent value.
 * contributors is the current cycle's contributors, sorted descending and capped server-side
 * (see FishCatchSavedData#getCleanupGoalContributors) so the packet stays small.
 */
public record CleanupGoalProgress(int total, int threshold, int milestoneReached,
                                   List<CleanupGoalProgress.Contributor> contributors) {
    public static final CleanupGoalProgress EMPTY = new CleanupGoalProgress(0, 0, 0, List.of());

    public record Contributor(UUID playerUuid, String playerName, int amount) {
        public static final BufCodec<Contributor> STREAM_CODEC = BufCodec.composite(
                BufCodecs.UUID, Contributor::playerUuid,
                BufCodecs.stringUtf8(256), Contributor::playerName,
                BufCodecs.VAR_INT, Contributor::amount,
                Contributor::new
        );
    }

    public static final BufCodec<CleanupGoalProgress> STREAM_CODEC = BufCodec.composite(
            BufCodecs.VAR_INT, CleanupGoalProgress::total,
            BufCodecs.VAR_INT, CleanupGoalProgress::threshold,
            BufCodecs.VAR_INT, CleanupGoalProgress::milestoneReached,
            Contributor.STREAM_CODEC.apply(BufCodecs.list()), CleanupGoalProgress::contributors,
            CleanupGoalProgress::new
    );
}
