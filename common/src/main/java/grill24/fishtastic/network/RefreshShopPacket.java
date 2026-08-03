package grill24.fishtastic.network;

import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sent when the player clicks the shop's refresh button to spend tokens rerolling today's active entries. */
public record RefreshShopPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RefreshShopPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.REFRESH_SHOP_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshShopPacket> STREAM_CODEC =
            StreamCodec.unit(new RefreshShopPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(RefreshShopPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server == null) return;

            FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
            PlayerQuestState state = data.getOrCreateQuestState(serverPlayer);

            if (!state.refreshShop(ShopEntry.SHOP_REFRESH_COST)) return;

            data.setDirty();
            QuestSyncPacket.sendToPlayer(serverPlayer, data);
        });
    }
}
