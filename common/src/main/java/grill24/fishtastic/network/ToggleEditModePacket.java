package grill24.fishtastic.network;

import grill24.fishtastic.block.FishTankEditModeManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

/** Sent CLIENT → SERVER to toggle fish tank edit mode for the sending player. */
public record ToggleEditModePacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleEditModePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.TOGGLE_EDIT_MODE_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleEditModePacket> STREAM_CODEC =
            StreamCodec.unit(new ToggleEditModePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(ToggleEditModePacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> {
            if (!(context.getPlayer() instanceof ServerPlayer player)) return;
            boolean nowActive = FishTankEditModeManager.toggle(player.getUUID());
            player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal(nowActive ? "Fish Tank Edit Mode: ON" : "Fish Tank Edit Mode: OFF")
            ));
        });
    }
}
