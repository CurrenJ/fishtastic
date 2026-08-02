package grill24.fishtastic.network;

import grill24.FishtasticRegistries;
import grill24.fishtastic.client.FishEncyclopediaClientHelper;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Client→server claim for one fish's reward slot; server re-derives unlock state itself, like {@link CompleteQuestPacket}. */
public record ClaimEncyclopediaRewardPacket(Identifier fishId, EncyclopediaRewardSection section) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimEncyclopediaRewardPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.CLAIM_ENCYCLOPEDIA_REWARD_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimEncyclopediaRewardPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC,
                    ClaimEncyclopediaRewardPacket::fishId,
                    ByteBufCodecs.VAR_INT.map(i -> EncyclopediaRewardSection.values()[i], EncyclopediaRewardSection::ordinal),
                    ClaimEncyclopediaRewardPacket::section,
                    ClaimEncyclopediaRewardPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(ClaimEncyclopediaRewardPacket packet, FishtasticPackets.IPacketContext ctx) {
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
