package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
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
) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<FinishFishingMinigamePacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.FINISH_FISHING_MINIGAME_ID);

    // A session never generates more than a handful of targets (see FishingMinigameManager.MAX_TARGETS);
    // capping the list size closes off oversized/replayed-index payloads from a modified client.
    private static final int MAX_CAUGHT_INDICES = 16;

    public static final BufCodec<FinishFishingMinigamePacket> STREAM_CODEC = BufCodec.composite(
            BufCodecs.VAR_INT,
            FinishFishingMinigamePacket::sessionId,
            BufCodecs.VAR_INT.apply(BufCodecs.list(MAX_CAUGHT_INDICES)),
            FinishFishingMinigamePacket::caughtTargetIndices,
            FinishFishingMinigamePacket::new
    );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    /**
     * Handle packet on server side (client reports results)
     */
    public static void handleClientToServer(FinishFishingMinigamePacket packet, FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> {
            var player = context.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            Fishtastic.LOGGER.info("Player {} finished fishing minigame session {} - caught {} targets",
                    serverPlayer.getName().getString(), packet.sessionId, packet.caughtTargetIndices.size());

            // Get server-side minigame manager and process results
            var manager = grill24.fishtastic.server.FishingMinigameManager.get(serverPlayer.serverLevel());
            if (manager != null) {
                manager.handleMinigameComplete(serverPlayer, packet.sessionId, packet.caughtTargetIndices);
            }
        });
    }
}
