package grill24.fishtastic.network;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
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
public record ClaimAllEncyclopediaRewardsPacket(Optional<Identifier> fishId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimAllEncyclopediaRewardsPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.CLAIM_ALL_ENCYCLOPEDIA_REWARDS_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimAllEncyclopediaRewardsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC),
                    ClaimAllEncyclopediaRewardsPacket::fishId,
                    ClaimAllEncyclopediaRewardsPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Claims every section of {@code fishId} whose threshold {@code catchCount} has reached.
     * Returns how many sections were newly claimed (already-claimed ones are skipped).
     */
    public static int claimFish(PlayerQuestState state, Identifier fishId,
                                FishEncyclopediaEntry.UnlockThresholds thresholds, int catchCount) {
        int claimed = 0;
        for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
            if (catchCount < section.threshold(thresholds)) continue;
            if (state.claimEncyclopediaReward(fishId, section)) claimed++;
        }
        return claimed;
    }

    /** Claims across {@code fishIds}; the per-fish thresholds and catch counts come from the given lookups. */
    public static int claimAll(PlayerQuestState state, Iterable<Identifier> fishIds,
                               Function<Identifier, FishEncyclopediaEntry.UnlockThresholds> thresholds,
                               ToIntFunction<Identifier> catchCount) {
        int claimed = 0;
        for (Identifier fishId : fishIds) {
            claimed += claimFish(state, fishId, thresholds.apply(fishId), catchCount.applyAsInt(fishId));
        }
        return claimed;
    }

    public static void handleClientToServer(ClaimAllEncyclopediaRewardsPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server == null) return;

            FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
            UUID key = data.resolvePlayerKey(serverPlayer);
            PlayerQuestState state = data.getOrCreateQuestState(serverPlayer);

            List<Identifier> fishIds = packet.fishId().isPresent()
                    ? List.of(packet.fishId().get())
                    : server.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
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
