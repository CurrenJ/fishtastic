package grill24.fishtastic.network;

import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-to-client encyclopedia state, including species observed through a spyglass. */
public record FishEncyclopediaSyncPacket(
        Map<ResourceLocation, Integer> personalCatchCounts,
        List<LeaderboardEntry> personalBestSizes,
        List<LeaderboardEntry> globalBestSizes,
        List<String> claimedRewardKeys,
        List<ResourceLocation> observedSpecies,
        List<ResourceLocation> newlyObserved
) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<FishEncyclopediaSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.FISH_ENCYCLOPEDIA_SYNC_ID);

    public static final BufCodec<FishEncyclopediaSyncPacket> STREAM_CODEC = BufCodec.composite(
            BufCodecs.map(HashMap::new, BufCodecs.RESOURCE_LOCATION, BufCodecs.VAR_INT),
            FishEncyclopediaSyncPacket::personalCatchCounts,
            LeaderboardEntry.STREAM_CODEC.apply(BufCodecs.list()),
            FishEncyclopediaSyncPacket::personalBestSizes,
            LeaderboardEntry.STREAM_CODEC.apply(BufCodecs.list()),
            FishEncyclopediaSyncPacket::globalBestSizes,
            BufCodecs.STRING_UTF8.apply(BufCodecs.list()),
            FishEncyclopediaSyncPacket::claimedRewardKeys,
            BufCodecs.RESOURCE_LOCATION.apply(BufCodecs.list()),
            FishEncyclopediaSyncPacket::observedSpecies,
            BufCodecs.RESOURCE_LOCATION.apply(BufCodecs.list()),
            FishEncyclopediaSyncPacket::newlyObserved,
            FishEncyclopediaSyncPacket::new
    );

    @Override
    public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data) {
        sendToPlayer(player, data, List.of());
    }

    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data, List<ResourceLocation> newlyObserved) {
        java.util.UUID key = data.resolvePlayerKey(player);
        Map<ResourceLocation, Integer> catchCounts = new HashMap<>();
        data.getPersonalCatchCounts(key, FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC)
                .forEach(e -> catchCounts.put(e.fishType(), e.totalCatches()));
        List<LeaderboardEntry> personalBest = data.getPersonalBestSizes(key, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC)
                .stream().map(e -> LeaderboardEntry.personalBestSize(e.fishType(), e.bestSize(), e.bestQuality())).toList();
        List<LeaderboardEntry> globalBest = data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_DESC)
                .stream().map(e -> LeaderboardEntry.globalBestSize(e.fishType(), e.playerUuid(), e.playerName(), e.bestSize(), e.bestQuality())).toList();
        List<String> claimedRewardKeys = new ArrayList<>(data.getOrCreateQuestState(player).getClaimedEncyclopediaRewardsSnapshot());
        FishEncyclopediaSyncPacket packet = new FishEncyclopediaSyncPacket(catchCounts, personalBest, globalBest,
                claimedRewardKeys, new ArrayList<>(data.getObserved(key)), List.copyOf(newlyObserved));
        NetworkApiSided.sendToPlayer(player, packet, STREAM_CODEC);
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler { void handle(FishEncyclopediaSyncPacket packet); }

    public static void registerClientHandler(ClientHandler handler) { clientHandler = handler; }

    public static void handleServerToClient(FishEncyclopediaSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> { if (clientHandler != null) clientHandler.handle(packet); });
    }
}