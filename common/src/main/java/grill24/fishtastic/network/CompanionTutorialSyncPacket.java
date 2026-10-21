package grill24.fishtastic.network;

import grill24.fishtastic.network.FishtasticTransport;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record CompanionTutorialSyncPacket(CompanionTutorialStep step) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CompanionTutorialSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("companion_tutorial_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTutorialSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    CompanionTutorialStep.STREAM_CODEC,
                    CompanionTutorialSyncPacket::step,
                    CompanionTutorialSyncPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(CompanionTutorialSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(CompanionTutorialSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, CompanionTutorialStep step) {
        FishtasticTransport.sendToPlayer(player, new CompanionTutorialSyncPacket(step));
    }
}
