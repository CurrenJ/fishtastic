package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.CompanionTutorialManager;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

public record CompanionTutorialAdvancePacket(CompanionTutorialStep fromStep, boolean skip) implements FishtasticPayload {

    public CompanionTutorialAdvancePacket(CompanionTutorialStep fromStep) {
        this(fromStep, false);
    }

    public static final FishtasticPayload.PayloadType<CompanionTutorialAdvancePacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("companion_tutorial_advance"));

    public static final BufCodec<CompanionTutorialAdvancePacket> STREAM_CODEC =
            BufCodec.composite(
                    CompanionTutorialStep.STREAM_CODEC,
                    CompanionTutorialAdvancePacket::fromStep,
                    BufCodecs.BOOL,
                    CompanionTutorialAdvancePacket::skip,
                    CompanionTutorialAdvancePacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(CompanionTutorialAdvancePacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                CompanionTutorialManager.advanceStep(serverPlayer, packet.fromStep(), packet.skip());
            }
        });
    }
}
