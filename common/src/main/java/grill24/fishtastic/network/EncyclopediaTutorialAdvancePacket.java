package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import grill24.fishtastic.tutorial.EncyclopediaTutorialStep;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

public record EncyclopediaTutorialAdvancePacket(EncyclopediaTutorialStep fromStep) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<EncyclopediaTutorialAdvancePacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("encyclopedia_tutorial_advance"));

    public static final BufCodec<EncyclopediaTutorialAdvancePacket> STREAM_CODEC =
            BufCodec.composite(
                    EncyclopediaTutorialStep.STREAM_CODEC,
                    EncyclopediaTutorialAdvancePacket::fromStep,
                    EncyclopediaTutorialAdvancePacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(EncyclopediaTutorialAdvancePacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                EncyclopediaTutorialManager.advanceStep(serverPlayer, packet.fromStep());
            }
        });
    }
}
