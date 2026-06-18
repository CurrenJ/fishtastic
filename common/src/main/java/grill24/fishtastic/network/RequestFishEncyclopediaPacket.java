package grill24.fishtastic.network;

import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record RequestFishEncyclopediaPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFishEncyclopediaPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.REQUEST_FISH_ENCYCLOPEDIA_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFishEncyclopediaPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestFishEncyclopediaPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(RequestFishEncyclopediaPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                var server = ((ServerLevel) serverPlayer.level()).getServer();
                if (server != null) {
                    FishEncyclopediaSyncPacket.sendToPlayer(serverPlayer, FishCatchSavedData.getOrCreate(server));
                }
                GelatinOpenMenuCompat.openFishEncyclopediaMenu(serverPlayer);
            }
        });
    }
}
