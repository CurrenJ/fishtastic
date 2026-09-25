package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

public record TutorialSyncPacket(TutorialStep step) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<TutorialSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("tutorial_sync"));

    public static final BufCodec<TutorialSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    TutorialStep.STREAM_CODEC,
                    TutorialSyncPacket::step,
                    TutorialSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(TutorialSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(TutorialSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, TutorialStep step) {
        NetworkApiSided.sendToPlayer(player, new TutorialSyncPacket(step), STREAM_CODEC);
    }
}
