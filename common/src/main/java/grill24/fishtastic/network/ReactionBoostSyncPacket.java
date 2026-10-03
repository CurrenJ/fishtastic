package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client how much faster cosmetic reaction clocks should run
 * (docs/fish-shelters.md §12.13): the fish simulation, and so every reaction, lives on the client.
 * A dev setting, not persisted; round-tripped from {@code /fishtastic cosmetic reactionboost} to the
 * same player, like {@link TankInteriorLightSyncPacket}.
 */
public record ReactionBoostSyncPacket(float boost) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReactionBoostSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("reaction_boost_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReactionBoostSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    ReactionBoostSyncPacket::boost,
                    ReactionBoostSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(ReactionBoostSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(ReactionBoostSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, float boost) {
        player.connection.send(new ClientboundCustomPayloadPacket(new ReactionBoostSyncPacket(boost)));
    }
}
