package grill24.fishtastic.network;

import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server → client: "this player's companion party is now this" (docs/fish-companions-party.md).
 * PORT-ONLY (1.20.1): the other versions use a platform attachment that syncs itself; here the
 * party lives in {@code CompanionPartySavedData} (server only), so the client's view of every
 * player's party is fed by this packet. An empty party removes the client entry.
 */
public record CompanionPartySyncPacket(UUID owner, CompanionParty party) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<CompanionPartySyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.COMPANION_PARTY_SYNC_ID);

    public static final BufCodec<CompanionPartySyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.UUID, CompanionPartySyncPacket::owner,
                    CompanionParty.STREAM_CODEC, CompanionPartySyncPacket::party,
                    CompanionPartySyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleServerToClient(CompanionPartySyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> CompanionStorage.applyClientParty(packet.owner(), packet.party()));
    }

    public static void sendToPlayer(ServerPlayer to, UUID owner, CompanionParty party) {
        NetworkApiSided.sendToPlayer(to, new CompanionPartySyncPacket(owner, party), STREAM_CODEC);
    }
}
