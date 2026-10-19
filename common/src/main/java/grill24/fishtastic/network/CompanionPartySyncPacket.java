package grill24.fishtastic.network;

import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionPartyClientCache;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server to client: "this player's companion party is now this". An empty party removes the client
 * entry. Forge has no player-attachment auto-sync, so this packet is the sole client feed.
 */
public record CompanionPartySyncPacket(UUID owner, CompanionParty party) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CompanionPartySyncPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.COMPANION_PARTY_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionPartySyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, CompanionPartySyncPacket::owner,
                    CompanionParty.STREAM_CODEC, CompanionPartySyncPacket::party,
                    CompanionPartySyncPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServerToClient(CompanionPartySyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> CompanionPartyClientCache.apply(packet.owner(), packet.party()));
    }

    public static void sendToPlayer(ServerPlayer to, UUID owner, CompanionParty party) {
        FishtasticTransport.sendToPlayer(to, new CompanionPartySyncPacket(owner, party));
    }

    /** Sends every online player's party to {@code to} (join/late-join/re-track prototype). */
    public static void syncAllTo(ServerPlayer to) {
        var server = to.level().getServer();
        if (server == null) return;
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            sendToPlayer(to, owner.getUUID(), grill24.fishtastic.companion.CompanionStorage.party(owner));
        }
    }
}
