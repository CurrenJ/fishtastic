package grill24.fishtastic.network;

import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Requests a fresh {@link FishEncyclopediaSyncPacket}. {@code openMenu} additionally opens the
 * encyclopedia menu server-side (used when navigating there); a sync-only request (e.g. so the
 * quest log's silhouettes are current without ever having opened the encyclopedia) passes false.
 */
public record RequestFishEncyclopediaPacket(boolean openMenu) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFishEncyclopediaPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.REQUEST_FISH_ENCYCLOPEDIA_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFishEncyclopediaPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RequestFishEncyclopediaPacket::openMenu,
                    RequestFishEncyclopediaPacket::new
            );

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
                if (packet.openMenu()) {
                    GelatinOpenMenuCompat.openFishEncyclopediaMenu(serverPlayer);
                    EncyclopediaTutorialManager.onEncyclopediaOpened(serverPlayer);
                }
            }
        });
    }
}
