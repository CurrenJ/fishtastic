package grill24.fishtastic.network;

import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server→client sync of catch counts, Records panel data, and claimed encyclopedia reward slots. */
public record FishEncyclopediaSyncPacket(
        Map<ResourceLocation, Integer> personalCatchCounts,
        List<LeaderboardEntry> personalBestSizes,
        List<LeaderboardEntry> globalBestSizes,
        List<String> claimedRewardKeys
) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<FishEncyclopediaSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.FISH_ENCYCLOPEDIA_SYNC_ID);

    public static final BufCodec<FishEncyclopediaSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.map(HashMap::new, BufCodecs.RESOURCE_LOCATION, BufCodecs.VAR_INT),
                    FishEncyclopediaSyncPacket::personalCatchCounts,
                    LeaderboardEntry.STREAM_CODEC.apply(BufCodecs.list()),
                    FishEncyclopediaSyncPacket::personalBestSizes,
                    LeaderboardEntry.STREAM_CODEC.apply(BufCodecs.list()),
                    FishEncyclopediaSyncPacket::globalBestSizes,
                    BufCodecs.STRING_UTF8.apply(BufCodecs.list()),
                    FishEncyclopediaSyncPacket::claimedRewardKeys,
                    FishEncyclopediaSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data) {
        java.util.UUID key = data.resolvePlayerKey(player);
        Map<ResourceLocation, Integer> catchCounts = new HashMap<>();
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

        List<String> claimedRewardKeys = new ArrayList<>(data.getOrCreateQuestState(player).getClaimedEncyclopediaRewardsSnapshot());

        FishEncyclopediaSyncPacket packet = new FishEncyclopediaSyncPacket(catchCounts, personalBest, globalBest, claimedRewardKeys);
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
