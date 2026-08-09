package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.EncyclopediaTutorialStep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record EncyclopediaTutorialSyncPacket(EncyclopediaTutorialStep step) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EncyclopediaTutorialSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("encyclopedia_tutorial_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncyclopediaTutorialSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    EncyclopediaTutorialStep.STREAM_CODEC,
                    EncyclopediaTutorialSyncPacket::step,
                    EncyclopediaTutorialSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(EncyclopediaTutorialSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(EncyclopediaTutorialSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, EncyclopediaTutorialStep step) {
        player.connection.send(new ClientboundCustomPayloadPacket(new EncyclopediaTutorialSyncPacket(step)));
    }
}
