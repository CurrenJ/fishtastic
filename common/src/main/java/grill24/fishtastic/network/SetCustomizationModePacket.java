package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.block.FishTankCustomizationMode;
import grill24.fishtastic.block.FishTankCustomizationModeManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent from CLIENT to SERVER to notify which fish tank customization mode the
 * player has selected (Frame / Sand / Glass).  Without this sync the server
 * always defaults to FRAME regardless of the player's scroll selection.
 */
public record SetCustomizationModePacket(int modeOrdinal) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetCustomizationModePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.SET_CUSTOMIZATION_MODE_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCustomizationModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SetCustomizationModePacket::modeOrdinal,
                    SetCustomizationModePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle packet on the server: update the server-side mode map for this player.
     */
    public static void handleClientToServer(SetCustomizationModePacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> {
            var player = context.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            FishTankCustomizationMode[] values = FishTankCustomizationMode.values();
            int ordinal = packet.modeOrdinal();
            if (ordinal < 0 || ordinal >= values.length) {
                Fishtastic.LOGGER.warn("[SetCustomizationModePacket] Invalid mode ordinal {} from player {}",
                        ordinal, serverPlayer.getName().getString());
                return;
            }

            FishTankCustomizationMode mode = values[ordinal];
            FishTankCustomizationModeManager.setMode(serverPlayer.getUUID(), mode);
            Fishtastic.LOGGER.info("[SetCustomizationModePacket] Player {} set customization mode to {}",
                    serverPlayer.getName().getString(), mode);
        });
    }
}

