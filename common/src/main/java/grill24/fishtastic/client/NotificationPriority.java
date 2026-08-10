package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestDifficulty;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Display priority for quest notification banners, declared highest priority first.
 * QuestProgressNotificationManager activates all pending banners of the current lowest
 * ordinal (highest priority) tier before any higher-ordinal tier is allowed to start —
 * a tier already on screen is never preempted, but the next tier only gets its turn once
 * the current one has fully drained (nothing left active or pending at that tier).
 * QuestProgressNotification also uses this to pick the activation sound.
 */
public enum NotificationPriority {
    NEW_SPECIES,
    PROGRESS_BRONZE,
    PROGRESS_SILVER,
    PROGRESS_GOLD,
    COMPLETION_BRONZE,
    COMPLETION_SILVER,
    COMPLETION_GOLD,
    OTHER;

    public static NotificationPriority classify(QuestProgressEvent event) {
        Identifier questId = event.questId();
        if (questId.getPath().startsWith(QuestProgressNotificationManager.FIRST_CATCH_ID_PREFIX)) {
            return NEW_SPECIES;
        }
        if (questId.equals(QuestProgressNotificationManager.OUT_OF_BAIT_ID)
                || questId.equals(QuestProgressNotificationManager.CLEANUP_GOAL_MILESTONE_ID)) {
            return OTHER;
        }

        QuestDifficulty difficulty = resolveDifficulty(questId);
        if (event.completed()) {
            return switch (difficulty) {
                case BRONZE -> COMPLETION_BRONZE;
                case SILVER -> COMPLETION_SILVER;
                case GOLD -> COMPLETION_GOLD;
            };
        } else {
            return switch (difficulty) {
                case BRONZE -> PROGRESS_BRONZE;
                case SILVER -> PROGRESS_SILVER;
                case GOLD -> PROGRESS_GOLD;
            };
        }
    }

    private static QuestDifficulty resolveDifficulty(Identifier questId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return QuestDifficulty.BRONZE;
        try {
            Registry<Quest> questRegistry = mc.level.registryAccess()
                    .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
            ResourceKey<Quest> questKey = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, questId);
            Quest quest = questRegistry.getOptional(questKey).orElse(null);
            return quest != null ? quest.difficulty() : QuestDifficulty.BRONZE;
        } catch (Exception ignored) {
            return QuestDifficulty.BRONZE;
        }
    }
}
