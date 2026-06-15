package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.tutorial.TutorialStep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record TutorialAdvancePacket(TutorialStep fromStep) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TutorialAdvancePacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("tutorial_advance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TutorialAdvancePacket> STREAM_CODEC =
            StreamCodec.composite(
                    TutorialStep.STREAM_CODEC,
                    TutorialAdvancePacket::fromStep,
                    TutorialAdvancePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
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
