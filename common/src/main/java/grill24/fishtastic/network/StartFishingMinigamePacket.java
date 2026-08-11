package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.PhaseRule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Sent from server to client to start a fishing minigame session.
 * Contains the session ID and all target items with their properties.
 */
/**
 * @param undiscoveredSpecies item ids appearing in this session — among the rewards and the
 *                            almanac previews — that the player has never caught. Computed
 *                            server-side because the client has no standing record of its own:
 *                            {@code FishEncyclopediaClientCache} is only populated while the
 *                            encyclopedia screen is open, so outside that screen it reports every
 *                            species as never-caught.
 */
public record StartFishingMinigamePacket(
        int sessionId,
        List<TargetData> targets,
        boolean isTutorial,
        List<ItemStack> topWeightedFishPreviews,
        Set<FishProfile.Zone> zones,
        Set<Identifier> undiscoveredSpecies
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartFishingMinigamePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.START_FISHING_MINIGAME_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, StartFishingMinigamePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            StartFishingMinigamePacket::sessionId,
            TargetData.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StartFishingMinigamePacket::targets,
            ByteBufCodecs.BOOL,
            StartFishingMinigamePacket::isTutorial,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StartFishingMinigamePacket::topWeightedFishPreviews,
            ByteBufCodecs.INT.map(
                    i -> FishProfile.Zone.values()[i],
                    Enum::ordinal
            ).apply(ByteBufCodecs.list()).map(Set::copyOf, List::copyOf),
            StartFishingMinigamePacket::zones,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Set::copyOf, List::copyOf),
            StartFishingMinigamePacket::undiscoveredSpecies,
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
     * Data structure for a single fishing target.
     * Phases carry the full movement personality — pattern selection and param overrides.
     */
    public record TargetData(
            List<ItemStack> rewardStacks,
            grill24.fishtastic.util.FishingTarget.TargetCategory category,
            float initialPosition,
            float difficulty,
            List<PhaseRule> phases
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
                PhaseRule.STREAM_CODEC.apply(ByteBufCodecs.list()),
                TargetData::phases,
                TargetData::new
        );
    }
}
