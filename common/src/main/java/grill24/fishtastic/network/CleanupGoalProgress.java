package grill24.fishtastic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

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
        public static final StreamCodec<ByteBuf, Contributor> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Contributor::playerUuid,
                ByteBufCodecs.stringUtf8(256), Contributor::playerName,
                ByteBufCodecs.VAR_INT, Contributor::amount,
                Contributor::new
        );
    }

    public static final StreamCodec<ByteBuf, CleanupGoalProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CleanupGoalProgress::total,
            ByteBufCodecs.VAR_INT, CleanupGoalProgress::threshold,
            ByteBufCodecs.VAR_INT, CleanupGoalProgress::milestoneReached,
            Contributor.STREAM_CODEC.apply(ByteBufCodecs.list()), CleanupGoalProgress::contributors,
            CleanupGoalProgress::new
    );
}
