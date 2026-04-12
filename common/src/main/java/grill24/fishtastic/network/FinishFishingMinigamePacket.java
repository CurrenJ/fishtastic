package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Sent from CLIENT to SERVER to report minigame completion.
 * Contains which targets were successfully caught (by index).
 * Server validates and awards the pre-determined rewards.
 */
public record FinishFishingMinigamePacket(
        int sessionId,
        List<Integer> caughtTargetIndices  // Indices of targets that were caught
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FinishFishingMinigamePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.FINISH_FISHING_MINIGAME_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FinishFishingMinigamePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FinishFishingMinigamePacket::sessionId,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
            FinishFishingMinigamePacket::caughtTargetIndices,
            FinishFishingMinigamePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle packet on server side (client reports results)
     */
    public static void handleClientToServer(FinishFishingMinigamePacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> {
            var player = context.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            Fishtastic.LOGGER.info("Player {} finished fishing minigame session {} - caught {} targets",
                    serverPlayer.getName().getString(), packet.sessionId, packet.caughtTargetIndices.size());

            // Get server-side minigame manager and process results
            var manager = grill24.fishtastic.server.FishingMinigameManager.get(serverPlayer.level());
            if (manager != null) {
                manager.handleMinigameComplete(serverPlayer, packet.sessionId, packet.caughtTargetIndices);
            }
        });
    }
}
