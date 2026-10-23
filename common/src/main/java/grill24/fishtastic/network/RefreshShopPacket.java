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

/** Sent when the player clicks the shop's refresh button to reroll today's active entries. */
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

            String result = applyReroll(state, server.overworld().getGameTime() / 24000L);
            if (!"success".equals(result)) return result;
            data.setDirty();
            return result;
            });
        });
    }

    /** Applies the authoritative shop-button action; shared with GameTests for interaction coverage. */
    public static String applyReroll(PlayerQuestState state, long currentDay) {
        state.resetDailyPurchasesIfNeeded(currentDay);
        // Price is read from the player's current reroll count, so the server — not the
        // client's rendered label — decides what this reroll costs.
        if (!state.refreshShop(ShopEntry.refreshCost(state.getShopRefreshCount()))) return "insufficient_tokens";
        return "success";
    }
}
