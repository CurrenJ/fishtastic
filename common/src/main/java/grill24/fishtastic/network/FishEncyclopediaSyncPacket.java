package grill24.fishtastic.network;

import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server→client sync of the data the fish encyclopedia screen needs: personal catch counts
 * for every fish the player has ever caught (drives home-screen silhouette state), and the
 * Records panel data (personal/global best sizes), reusing {@link LeaderboardEntry} as the
 * wire DTO exactly as {@code LeaderboardResponsePacket} does.
 */
public record FishEncyclopediaSyncPacket(
        Map<Identifier, Integer> personalCatchCounts,
        List<LeaderboardEntry> personalBestSizes,
        List<LeaderboardEntry> globalBestSizes
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FishEncyclopediaSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.FISH_ENCYCLOPEDIA_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FishEncyclopediaSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT),
                    FishEncyclopediaSyncPacket::personalCatchCounts,
                    LeaderboardEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    FishEncyclopediaSyncPacket::personalBestSizes,
                    LeaderboardEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    FishEncyclopediaSyncPacket::globalBestSizes,
                    FishEncyclopediaSyncPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data) {
        java.util.UUID key = data.resolvePlayerKey(player);
        Map<Identifier, Integer> catchCounts = new HashMap<>();
        data.getPersonalCatchCounts(key, FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC)
                .forEach(e -> catchCounts.put(e.fishType(), e.totalCatches()));

        List<LeaderboardEntry> personalBest = data.getPersonalBestSizes(key, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC)
                .stream()
                .map(e -> LeaderboardEntry.personalBestSize(e.fishType(), e.bestSize(), e.bestQuality()))
                .toList();

        List<LeaderboardEntry> globalBest = data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_DESC)
                .stream()
                .map(e -> LeaderboardEntry.globalBestSize(e.fishType(), e.playerUuid(), e.playerName(), e.bestSize(), e.bestQuality()))
                .toList();

        FishEncyclopediaSyncPacket packet = new FishEncyclopediaSyncPacket(catchCounts, personalBest, globalBest);
        player.connection.send(new ClientboundCustomPayloadPacket(packet));
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(FishEncyclopediaSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(FishEncyclopediaSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }
}
