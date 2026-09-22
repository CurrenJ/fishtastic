package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sent <strong>client → server</strong> to request leaderboard data.
 *
 * <p>{@code targetPlayer} is currently unused by the server: personal leaderboard types
 * ({@link LeaderboardType#PERSONAL_BEST_SIZE}, {@link LeaderboardType#PERSONAL_CATCH_COUNT})
 * always resolve to the requester's own key via {@link FishCatchSavedData#resolvePlayerKey},
 * never a client-supplied UUID — the singleplayer owner's catches are stored under a fixed
 * synthetic key that differs from their real UUID, so trusting the raw client value would break
 * personal boards in singleplayer. Global types ignore this field entirely.
 *
 * <p>The server will respond with a {@link LeaderboardResponsePacket}.
 */
public record RequestLeaderboardPacket(
        LeaderboardType leaderboardType,
        boolean ascending,
        Optional<UUID> targetPlayer
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestLeaderboardPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.REQUEST_LEADERBOARD_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLeaderboardPacket> STREAM_CODEC =
            StreamCodec.composite(
                    LeaderboardType.STREAM_CODEC,
                    RequestLeaderboardPacket::leaderboardType,
                    ByteBufCodecs.BOOL,
                    RequestLeaderboardPacket::ascending,
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
                    RequestLeaderboardPacket::targetPlayer,
                    RequestLeaderboardPacket::new
            );

    @Override
    public CustomPacketPayload.Type<RequestLeaderboardPacket> type() {
        return TYPE;
    }

    /** Handles the packet on the server side. */
    public static void handleClientToServer(RequestLeaderboardPacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> {
            if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) return;

            FishCatchSavedData db = FishCatchSavedData.getOrCreate(serverPlayer.level().getServer());
            List<LeaderboardEntry> entries = buildEntries(packet, db, serverPlayer);

            LeaderboardResponsePacket response = new LeaderboardResponsePacket(
                    packet.leaderboardType(), packet.ascending(), entries);
            serverPlayer.connection.send(
                    new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(response));

            Fishtastic.LOGGER.debug("Sent {} leaderboard entries to {} (type={}, asc={})",
                    entries.size(), serverPlayer.getName().getString(), packet.leaderboardType(), packet.ascending());
        });
    }

    /** Rows the leaderboard screen draws as a podium, and so the only ones needing catch history. */
    private static final int PODIUM_SIZE = 3;

    private static List<LeaderboardEntry> buildEntries(RequestLeaderboardPacket packet,
                                                        FishCatchSavedData db,
                                                        ServerPlayer requester) {
        boolean asc = packet.ascending();
        // Personal boards always resolve to the requester's own key via resolvePlayerKey, never
        // the client-supplied targetPlayer directly — the singleplayer owner's catches are
        // recorded under a fixed synthetic UUID (see resolvePlayerKey), which differs from their
        // real player UUID, so trusting the raw client-sent UUID would always miss in singleplayer.
        UUID targetUuid = db.resolvePlayerKey(requester);

        return switch (packet.leaderboardType()) {
            case PERSONAL_BEST_SIZE -> db.getPersonalBestSizes(targetUuid,
                    asc ? FishCatchSavedData.PERSONAL_BEST_SIZE_ASC : FishCatchSavedData.PERSONAL_BEST_SIZE_DESC)
                    .stream()
                    .map(e -> LeaderboardEntry.personalBestSize(e.fishType(), e.bestSize(), e.bestQuality()))
                    .toList();

            case GLOBAL_BEST_SIZE -> db.getGlobalBestSizes(
                    asc ? FishCatchSavedData.GLOBAL_BEST_SIZE_ASC : FishCatchSavedData.GLOBAL_BEST_SIZE_DESC)
                    .stream()
                    .map(e -> LeaderboardEntry.globalBestSize(e.fishType(), e.playerUuid(), e.playerName(),
                            e.bestSize(), e.bestQuality()))
                    .toList();

            case PERSONAL_CATCH_COUNT -> db.getPersonalCatchCounts(targetUuid,
                    asc ? FishCatchSavedData.PERSONAL_CATCH_COUNT_ASC : FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC)
                    .stream()
                    .map(e -> LeaderboardEntry.personalCatchCount(e.fishType(), e.totalCatches()))
                    .toList();

            case GLOBAL_CATCH_COUNT -> {
                List<FishCatchSavedData.GlobalCatchCountEntry> counts = db.getGlobalCatchCounts(
                        asc ? FishCatchSavedData.GLOBAL_CATCH_COUNT_ASC : FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC);
                List<LeaderboardEntry> rows = new ArrayList<>(counts.size());
                for (int i = 0; i < counts.size(); i++) {
                    FishCatchSavedData.GlobalCatchCountEntry e = counts.get(i);
                    // Only the three podium rows render a Fish Pile of real catches, so only they
                    // carry the history - no point paying for it on a board of hundreds of rows.
                    List<RecentCatch> recent = i < PODIUM_SIZE
                            ? db.getRecentCatches(e.playerUuid(), FishCatchSavedData.MAX_RECENT_CATCHES)
                            : List.of();
                    rows.add(LeaderboardEntry.globalCatchCount(e.playerUuid(), e.playerName(), e.totalCatches(), recent));
                }
                yield List.copyOf(rows);
            }
        };
    }
}
