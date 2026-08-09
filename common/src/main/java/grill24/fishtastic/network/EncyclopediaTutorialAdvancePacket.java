package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import grill24.fishtastic.tutorial.EncyclopediaTutorialStep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record EncyclopediaTutorialAdvancePacket(EncyclopediaTutorialStep fromStep) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EncyclopediaTutorialAdvancePacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("encyclopedia_tutorial_advance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncyclopediaTutorialAdvancePacket> STREAM_CODEC =
            StreamCodec.composite(
                    EncyclopediaTutorialStep.STREAM_CODEC,
                    EncyclopediaTutorialAdvancePacket::fromStep,
                    EncyclopediaTutorialAdvancePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
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
