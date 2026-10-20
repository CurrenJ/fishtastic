package grill24.fishtastic.network;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Client→server claim of every unlocked, unclaimed encyclopedia reward: for one fish when
 * {@code fishId} is set, for the whole book when empty. Each section goes through the same
 * {@link PlayerQuestState#claimEncyclopediaReward} path as {@link ClaimEncyclopediaRewardPacket}
 * (idempotent per section, tokens included), and the server re-derives the thresholds itself.
 * One {@link FishEncyclopediaSyncPacket} is sent at the end, not one per claim.
 */
public record ClaimAllEncyclopediaRewardsPacket(Optional<ResourceLocation> fishId) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<ClaimAllEncyclopediaRewardsPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.CLAIM_ALL_ENCYCLOPEDIA_REWARDS_ID);

    public static final BufCodec<ClaimAllEncyclopediaRewardsPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.optional(BufCodecs.RESOURCE_LOCATION),
                    ClaimAllEncyclopediaRewardsPacket::fishId,
                    ClaimAllEncyclopediaRewardsPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    /**
     * Claims every section of {@code fishId} whose threshold {@code catchCount} has reached.
     * Returns how many sections were newly claimed (already-claimed ones are skipped).
     */
    public static int claimFish(PlayerQuestState state, ResourceLocation fishId,
                                FishEncyclopediaEntry.UnlockThresholds thresholds, int catchCount) {
        int claimed = 0;
        for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
            if (catchCount < section.threshold(thresholds)) continue;
            if (state.claimEncyclopediaReward(fishId, section)) claimed++;
        }
        return claimed;
    }

    /** Claims across {@code fishIds}; the per-fish thresholds and catch counts come from the given lookups. */
    public static int claimAll(PlayerQuestState state, Iterable<ResourceLocation> fishIds,
                               Function<ResourceLocation, FishEncyclopediaEntry.UnlockThresholds> thresholds,
                               ToIntFunction<ResourceLocation> catchCount) {
        int claimed = 0;
        for (ResourceLocation fishId : fishIds) {
            claimed += claimFish(state, fishId, thresholds.apply(fishId), catchCount.applyAsInt(fishId));
        }
        return claimed;
    }

    public static void handleClientToServer(ClaimAllEncyclopediaRewardsPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server == null) return;

            FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
            UUID key = data.resolvePlayerKey(serverPlayer);
            PlayerQuestState state = data.getOrCreateQuestState(serverPlayer);

            List<ResourceLocation> fishIds = packet.fishId().isPresent()
                    ? List.of(packet.fishId().get())
                    : server.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                            .keySet().stream().toList();
            int claimed = claimAll(state, fishIds,
                    id -> EncyclopediaTiers.thresholdsFor(server.registryAccess(), id),
                    id -> data.getCatchCount(key, id));
            if (claimed == 0) return;

            data.setDirty();
            FishEncyclopediaSyncPacket.sendToPlayer(serverPlayer, data);
        });
    }
}
