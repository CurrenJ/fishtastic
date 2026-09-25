package grill24.fishtastic.network;

import grill24.FishtasticRegistries;
import grill24.fishtastic.client.FishEncyclopediaClientHelper;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Client→server claim for one fish's reward slot; server re-derives unlock state itself, like {@link CompleteQuestPacket}. */
public record ClaimEncyclopediaRewardPacket(ResourceLocation fishId, EncyclopediaRewardSection section) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<ClaimEncyclopediaRewardPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.CLAIM_ENCYCLOPEDIA_REWARD_ID);

    public static final BufCodec<ClaimEncyclopediaRewardPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.RESOURCE_LOCATION,
                    ClaimEncyclopediaRewardPacket::fishId,
                    BufCodecs.VAR_INT.map(i -> EncyclopediaRewardSection.values()[i], EncyclopediaRewardSection::ordinal),
                    ClaimEncyclopediaRewardPacket::section,
                    ClaimEncyclopediaRewardPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(ClaimEncyclopediaRewardPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server == null) return;

            ResourceKey<FishProfile> fishKey = ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, packet.fishId());
            FishEncyclopediaEntry entry = FishEncyclopediaClientHelper.getEncyclopediaEntry(server.registryAccess(), fishKey);

            FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
            java.util.UUID key = data.resolvePlayerKey(serverPlayer);
            int catchCount = data.getCatchCount(key, packet.fishId());
            if (catchCount < packet.section().threshold(entry.thresholds())) return;

            PlayerQuestState state = data.getOrCreateQuestState(serverPlayer);
            if (!state.claimEncyclopediaReward(packet.fishId(), packet.section())) return;

            data.setDirty();
            FishEncyclopediaSyncPacket.sendToPlayer(serverPlayer, data);
        });
    }
}
