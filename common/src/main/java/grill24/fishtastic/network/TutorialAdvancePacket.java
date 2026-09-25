package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

public record TutorialAdvancePacket(TutorialStep fromStep) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<TutorialAdvancePacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("tutorial_advance"));

    public static final BufCodec<TutorialAdvancePacket> STREAM_CODEC =
            BufCodec.composite(
                    TutorialStep.STREAM_CODEC,
                    TutorialAdvancePacket::fromStep,
                    TutorialAdvancePacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(TutorialAdvancePacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                TutorialManager.advanceStep(serverPlayer, packet.fromStep());
            }
        });
    }
}
