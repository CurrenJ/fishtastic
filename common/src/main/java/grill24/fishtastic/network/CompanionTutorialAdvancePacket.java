package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.CompanionTutorialManager;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record CompanionTutorialAdvancePacket(CompanionTutorialStep fromStep, boolean skip) implements CustomPacketPayload {

    public CompanionTutorialAdvancePacket(CompanionTutorialStep fromStep) {
        this(fromStep, false);
    }

    public static final CustomPacketPayload.Type<CompanionTutorialAdvancePacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("companion_tutorial_advance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTutorialAdvancePacket> STREAM_CODEC =
            StreamCodec.composite(
                    CompanionTutorialStep.STREAM_CODEC,
                    CompanionTutorialAdvancePacket::fromStep,
                    net.minecraft.network.codec.ByteBufCodecs.BOOL,
                    CompanionTutorialAdvancePacket::skip,
                    CompanionTutorialAdvancePacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(CompanionTutorialAdvancePacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                CompanionTutorialManager.advanceStep(serverPlayer, packet.fromStep(), packet.skip());
            }
        });
    }
}
