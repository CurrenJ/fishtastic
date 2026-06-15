package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.TutorialStep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record TutorialSyncPacket(TutorialStep step) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TutorialSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("tutorial_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TutorialSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    TutorialStep.STREAM_CODEC,
                    TutorialSyncPacket::step,
                    TutorialSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(TutorialSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(TutorialSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, TutorialStep step) {
        player.connection.send(new ClientboundCustomPayloadPacket(new TutorialSyncPacket(step)));
    }
}
