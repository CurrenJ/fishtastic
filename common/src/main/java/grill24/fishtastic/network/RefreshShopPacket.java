package grill24.fishtastic.network;

import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sent when the player clicks the shop's refresh button to spend tokens rerolling today's active entries. */
public record RefreshShopPacket(long requestId) implements FishtasticPayload {

    public RefreshShopPacket() { this(0); }

    public static final FishtasticPayload.PayloadType<RefreshShopPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.REFRESH_SHOP_ID);

    public static final BufCodec<RefreshShopPacket> STREAM_CODEC =
            BufCodec.composite(BufCodecs.VAR_LONG, RefreshShopPacket::requestId, RefreshShopPacket::new);

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(RefreshShopPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server == null) return;

            QuestActionResultPacket.respond(serverPlayer, packet.requestId(), "reroll", grill24.fishtastic.Fishtastic.id("market"), () -> {
            FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
            PlayerQuestState state = data.getOrCreateQuestState(serverPlayer);

            state.resetDailyPurchasesIfNeeded(server.overworld().getGameTime() / 24000L);
            // Price is read from the player's current reroll count, so the server — not the
            // client's rendered label — decides what this reroll costs.
            if (!state.refreshShop(ShopEntry.refreshCost(state.getShopRefreshCount()))) return "insufficient_tokens";

            data.setDirty();
            return "success";
            });
        });
    }
}
