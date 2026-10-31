package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.ecology.EcologyClient;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

/** Syncs a per-dimension ecology seed to the client without exposing the world seed. */
public record EcologySeedSyncPacket(String dimension, long seed) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<EcologySeedSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.ECOLOGY_SEED_SYNC_ID);

    public static final BufCodec<EcologySeedSyncPacket> STREAM_CODEC = BufCodec.composite(
            BufCodecs.STRING_UTF8,
            EcologySeedSyncPacket::dimension,
            BufCodecs.VAR_LONG,
            EcologySeedSyncPacket::seed,
            EcologySeedSyncPacket::new
    );

    @Override
    public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleServerToClient(EcologySeedSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> EcologyClient.set(packet.dimension(), packet.seed()));
    }

    public static void sendToPlayer(ServerPlayer player, String dimension, long seed) {
        NetworkApiSided.sendToPlayer(player, new EcologySeedSyncPacket(dimension, seed), STREAM_CODEC);
    }
}