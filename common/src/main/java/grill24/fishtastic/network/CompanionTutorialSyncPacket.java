package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.server.level.ServerPlayer;

public record CompanionTutorialSyncPacket(CompanionTutorialStep step) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<CompanionTutorialSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("companion_tutorial_sync"));

    public static final BufCodec<CompanionTutorialSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    CompanionTutorialStep.STREAM_CODEC,
                    CompanionTutorialSyncPacket::step,
                    CompanionTutorialSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(CompanionTutorialSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(CompanionTutorialSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, CompanionTutorialStep step) {
        NetworkApiSided.sendToPlayer(player, new CompanionTutorialSyncPacket(step), STREAM_CODEC);
    }
}
