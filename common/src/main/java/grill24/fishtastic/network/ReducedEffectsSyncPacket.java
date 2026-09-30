package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to persist the reduced celebration effects setting into
 * {@link grill24.fishtastic.client.FishtasticClientConfig}. The setting is purely client-side (it
 * only changes how catch celebrations are drawn), so the server never stores it - like
 * {@link TankWaterFillSyncPacket}, this is round-tripped from the command handler straight back to
 * the same player.
 */
public record ReducedEffectsSyncPacket(boolean reduced) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReducedEffectsSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("reduced_effects_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReducedEffectsSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    ReducedEffectsSyncPacket::reduced,
                    ReducedEffectsSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(ReducedEffectsSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(ReducedEffectsSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, boolean reduced) {
        player.connection.send(new ClientboundCustomPayloadPacket(new ReducedEffectsSyncPacket(reduced)));
    }
}
