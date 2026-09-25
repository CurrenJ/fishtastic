package grill24.fishtastic.network;

import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Requests a fresh {@link FishEncyclopediaSyncPacket}. {@code openMenu} additionally opens the
 * encyclopedia menu server-side (used when navigating there); a sync-only request (e.g. so the
 * quest log's silhouettes are current without ever having opened the encyclopedia) passes false.
 */
public record RequestFishEncyclopediaPacket(boolean openMenu) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<RequestFishEncyclopediaPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.REQUEST_FISH_ENCYCLOPEDIA_ID);

    public static final BufCodec<RequestFishEncyclopediaPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.BOOL,
                    RequestFishEncyclopediaPacket::openMenu,
                    RequestFishEncyclopediaPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
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
