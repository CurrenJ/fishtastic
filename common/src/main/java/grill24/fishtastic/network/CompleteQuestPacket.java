package grill24.fishtastic.network;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.server.QuestTracker;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record CompleteQuestPacket(Identifier questId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CompleteQuestPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.COMPLETE_QUEST_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CompleteQuestPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC,
                    CompleteQuestPacket::questId,
                    CompleteQuestPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(CompleteQuestPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server == null) return;

            ResourceKey<Quest> questKey = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, packet.questId());

            Registry<Quest> questRegistry;
            try {
                questRegistry = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
            } catch (Exception e) {
                return;
            }

            Quest quest = questRegistry.getOptional(questKey).orElse(null);
            if (quest == null) return;

            FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
            PlayerQuestState state = data.getOrCreateQuestState(serverPlayer);

            int targetCount = quest.objective().effectiveTargetCount(server.registryAccess());
            if (!state.canClaim(questKey, targetCount)) return;

            for (QuestReward.RewardItem item : quest.reward().items()) {
                serverPlayer.getInventory().add(item.toStack());
            }
            state.claim(questKey, quest.reward().questTokens());
            if (quest.category() == QuestCategory.DAILY) {
                long currentDay = server.overworld().getGameTime() / 24000L;
                QuestTracker.onDailyQuestClaimed(server.registryAccess(), state, questRegistry, currentDay);
            }
            data.setDirty();

            TutorialManager.onQuestClaimed(serverPlayer, packet.questId());
            QuestSyncPacket.sendToPlayer(serverPlayer, data);
        });
    }
}
