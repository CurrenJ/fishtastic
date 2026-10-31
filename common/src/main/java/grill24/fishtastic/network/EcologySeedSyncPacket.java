package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.ecology.EcologyClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells a client the ecology seed of the dimension it is in (see
 * {@link grill24.fishtastic.ecology.EcologySeed}), so it can derive the same seeded ecology as the
 * server. Sent on login and on every dimension change by
 * {@link grill24.fishtastic.server.EcologySeedSync}. Carries only the derived seed, never the world
 * seed. The handler just stores it in {@link EcologyClient}, so no platform client hook is needed.
 */
public record EcologySeedSyncPacket(String dimension, long seed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EcologySeedSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("ecology_seed_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EcologySeedSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    EcologySeedSyncPacket::dimension,
            ByteBufCodecs.VAR_LONG,
                    EcologySeedSyncPacket::seed,
                    EcologySeedSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServerToClient(EcologySeedSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> EcologyClient.set(packet.dimension(), packet.seed()));
    }

    public static void sendToPlayer(ServerPlayer player, String dimension, long seed) {
        FishtasticTransport.sendToPlayer(player, new EcologySeedSyncPacket(dimension, seed));
    }
}
