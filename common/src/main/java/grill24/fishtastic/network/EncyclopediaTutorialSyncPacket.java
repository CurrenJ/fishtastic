package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.EncyclopediaTutorialStep;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

public record EncyclopediaTutorialSyncPacket(EncyclopediaTutorialStep step) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<EncyclopediaTutorialSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("encyclopedia_tutorial_sync"));

    public static final BufCodec<EncyclopediaTutorialSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    EncyclopediaTutorialStep.STREAM_CODEC,
                    EncyclopediaTutorialSyncPacket::step,
                    EncyclopediaTutorialSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(EncyclopediaTutorialSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(EncyclopediaTutorialSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, EncyclopediaTutorialStep step) {
        NetworkApiSided.sendToPlayer(player, new EncyclopediaTutorialSyncPacket(step), STREAM_CODEC);
    }
}
