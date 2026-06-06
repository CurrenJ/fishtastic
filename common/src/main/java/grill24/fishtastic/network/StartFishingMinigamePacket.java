package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Sent from server to client to start a fishing minigame session.
 * Contains the session ID and all target items with their properties.
 */
public record StartFishingMinigamePacket(
        int sessionId,
        List<TargetData> targets
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartFishingMinigamePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.START_FISHING_MINIGAME_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, StartFishingMinigamePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            StartFishingMinigamePacket::sessionId,
            TargetData.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StartFishingMinigamePacket::targets,
            StartFishingMinigamePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle packet on client side
     */
    public static void handle(StartFishingMinigamePacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> {
            Fishtastic.LOGGER.info("Received start fishing minigame packet: session={}, targets={}",
                    packet.sessionId, packet.targets.size());

            // Call client handler via reflection/runtime class to avoid compile-time dependency
            try {
                Class<?> handlerClass = Class.forName("grill24.fishtastic.client.FishingMinigameClientHandler");
                java.lang.reflect.Method handleMethod = handlerClass.getMethod("handleStartPacket", StartFishingMinigamePacket.class);
                handleMethod.invoke(null, packet);
            } catch (Exception e) {
                Fishtastic.LOGGER.error("Failed to handle start fishing minigame packet", e);
            }
        });
    }

    /**
     * Data structure for a single fishing target
     */
    public record TargetData(
            List<ItemStack> rewardStacks,
            grill24.fishtastic.util.FishingTarget.TargetCategory category,
            float initialPosition,
            float difficulty,
            grill24.fishtastic.util.FishingTarget.MovementPattern pattern
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TargetData> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                TargetData::rewardStacks,
                ByteBufCodecs.INT.map(
                        i -> grill24.fishtastic.util.FishingTarget.TargetCategory.values()[i],
                        Enum::ordinal
                ),
                TargetData::category,
                ByteBufCodecs.FLOAT,
                TargetData::initialPosition,
                ByteBufCodecs.FLOAT,
                TargetData::difficulty,
                ByteBufCodecs.INT.map(
                        i -> grill24.fishtastic.util.FishingTarget.MovementPattern.values()[i],
                        Enum::ordinal
                ),
                TargetData::pattern,
                TargetData::new
        );
    }
}
