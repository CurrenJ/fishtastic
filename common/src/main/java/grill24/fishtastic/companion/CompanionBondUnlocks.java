package grill24.fishtastic.companion;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server-side detection of newly unlocked companion bonds (docs/fish-companions.md, §8.4).
 * A normal species unlocks the moment its encyclopedia entry is fully complete (every threshold on
 * that one species — R6.5: claiming rewards is not required); an unlisted species only unlocks via a
 * quest carrying {@code unlocks_companion}, once that quest is completed or claimed. The transition
 * is stored in {@link PlayerQuestState} so a bond never re-locks (R6.4), and queued so the next
 * {@code QuestSyncPacket} announces it as a banner (R7.7).
 *
 * <p>{@link #refresh} is idempotent and cheap (a player's caught species against cached thresholds,
 * plus a scan of the quest registry's unlock hooks), so it can run on every quest sync without
 * every unlock site having to remember to call it. It deliberately takes the player, not a state:
 * resolving the singleplayer key is part of the storage contract.
 */
public final class CompanionBondUnlocks {
    private CompanionBondUnlocks() {}

    /**
     * Persists every bond this player has newly earned and returns the newly stored species (in
     * unlock order), for a caller that wants to force a sync. Safe to call anywhere, any number of
     * times: unpersisted transitions queue their own banners, which the sync drains.
     */
    public static List<Identifier> refresh(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return List.of();
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
        PlayerQuestState state = data.getOrCreateQuestState(player);
        UUID key = data.resolvePlayerKey(player);

        List<Identifier> newly = new ArrayList<>();
        for (FishCatchSavedData.PersonalCatchCountEntry entry : data.getPersonalCatchCounts(key,
                FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC)) {
            Identifier species = entry.fishType();
            if (state.isCompanionBondUnlocked(species) || isUnlisted(species)) continue;
            FishEncyclopediaEntry.UnlockThresholds thresholds =
                    EncyclopediaTiers.thresholdsFor(server.registryAccess(), species);
            if (isEntryComplete(entry.totalCatches(), thresholds) && state.unlockCompanionBond(species)) {
                newly.add(species);
            }
        }
        for (Identifier species : questUnlockedSpecies(server, state, key)) {
            if (state.unlockCompanionBond(species)) newly.add(species);
        }
        if (!newly.isEmpty()) {
            data.setDirty();
            // The first unlock ever starts the FTUE (a later unlock only gets the banner).
            grill24.fishtastic.tutorial.CompanionTutorialManager.onBondUnlocked(player);
        }
        return newly;
    }

    /** Species this player's done quests unlock; empty if the quest registry isn't present. */
    private static List<Identifier> questUnlockedSpecies(MinecraftServer server, PlayerQuestState state, UUID key) {
        Registry<Quest> questRegistry;
        try {
            questRegistry = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            return List.of();
        }
        return questUnlockedSpecies(questRegistry,
                questId -> {
                    PlayerQuestState.QuestProgress progress = state.getProgress(questId);
                    return progress.completed() || progress.claimed();
                },
                state::isCompanionBondUnlocked);
    }

    /**
     * Pure: every species a set of quest unlock hooks newly unlocks, given which quests count as
     * done and which species are already unlocked. Public so the game-test suite can drive it with
     * a fabricated registry; the live behaviour only differs in where the registry and progress
     * come from.
     */
    public static List<Identifier> questUnlockedSpecies(Registry<Quest> questRegistry,
                                                  Predicate<ResourceKey<Quest>> done,
                                                  Predicate<Identifier> alreadyUnlocked) {
        List<Identifier> newly = new ArrayList<>();
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
            if (!done.test(entry.getKey())) continue;
            Quest.companionUnlockedBy(entry.getValue(), true, false)
                    .filter(species -> !alreadyUnlocked.test(species))
                    .ifPresent(newly::add);
        }
        return newly;
    }

    /** Whether {@code count} catches complete the entry — every derived threshold met. */
    public static boolean isEntryComplete(int count, FishEncyclopediaEntry.UnlockThresholds thresholds) {
        for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
            if (count < section.threshold(thresholds)) return false;
        }
        return true;
    }

    /** Whether this species is a quest-only (unlisted) companion. */
    public static boolean isUnlisted(Identifier species) {
        return BuiltInRegistries.ITEM.getOptional(species)
                .map(item -> item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH))
                .orElse(false);
    }
}
