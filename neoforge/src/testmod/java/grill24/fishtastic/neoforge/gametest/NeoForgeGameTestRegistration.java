package grill24.fishtastic.neoforge.gametest;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.gametest.CapstoneRewardGameTests;
import grill24.fishtastic.gametest.CreativeTabGameTests;
import grill24.fishtastic.gametest.LifetimeQuestProgressGameTests;
import grill24.fishtastic.gametest.QuestLogVisibilityGameTests;
import grill24.fishtastic.gametest.QuestSatisfiabilityGameTests;
import grill24.fishtastic.gametest.EncyclopediaTutorialManagerGameTests;
import grill24.fishtastic.gametest.FishCatchDataGameTests;
import grill24.fishtastic.gametest.FishEncyclopediaClientGameTests;
import grill24.fishtastic.gametest.FishEncyclopediaEntryGameTests;
import grill24.fishtastic.gametest.FishTankGameTests;
import grill24.fishtastic.gametest.FishingMinigameManagerGameTests;
import grill24.fishtastic.gametest.FishingTargetGameTests;
import grill24.fishtastic.gametest.ItemComponentGameTests;
import grill24.fishtastic.gametest.ItemEffectConditionGameTests;
import grill24.fishtastic.gametest.MarineCompostGameTests;
import grill24.fishtastic.gametest.MathUtilGameTests;
import grill24.fishtastic.gametest.PacketRoundTripGameTests;
import grill24.fishtastic.gametest.PlayerQuestStateGameTests;
import grill24.fishtastic.gametest.QuestTrackerGameTests;
import grill24.fishtastic.gametest.ShopEntryGameTests;
import grill24.fishtastic.gametest.StormCharmGameTests;
import grill24.fishtastic.gametest.TutorialManagerGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

import static grill24.fishtastic.Fishtastic.MOD_ID;

/**
 * Registers all Fishtastic game tests with NeoForge's game test system.
 *
 * PREREQUISITE: Run `./gradlew :neoforge:runData` once to generate the empty
 * test structure file at data/fishtastic/structure/empty_testarea.nbt.
 * After generating, commit the file so CI doesn't need to regenerate it.
 *
 * Run tests: `./gradlew :neoforge:runGametest`
 */
@EventBusSubscriber(modid = MOD_ID)
public class NeoForgeGameTestRegistration {

    /** Structure used by all Fishtastic game tests — a 7×7×7 empty area. */
    private static final Identifier EMPTY_STRUCTURE =
        Identifier.fromNamespaceAndPath(MOD_ID, "empty_testarea");

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // RegisterGameTestsEvent fires on every dev-environment run (runClient, runServer, runData),
        // not just runGametest — and ServerModLoader.isGameTestServer() does NOT reliably distinguish
        // those (still true/irrelevant during a plain client run in this Loom setup, confirmed by the
        // crash persisting after gating on it). Registries.TEST_INSTANCE is a synchronized registry, so
        // anything registered here must survive being network-encoded for every connecting client (even
        // the integrated singleplayer server) via codec(). Our ConsumerTestInstance wraps a bare
        // Consumer<GameTestHelper> that can't be serialized, so instead we gate on the one signal we
        // fully control: the -Dneoforge.enabledGameTestNamespaces VM arg that only the :neoforge:runGametest
        // task sets (see neoforge/build.gradle) — no other run ever sets it.
        if (System.getProperty("neoforge.enabledGameTestNamespaces") == null) {
            return;
        }

        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(MOD_ID, "default"),
            new TestEnvironmentDefinition.AllOf(java.util.List.of())
        );

        // ----- Marine Compost tests -----
        register(event, env, "aeration_cap_at_five", 200,
            MarineCompostGameTests::aerationCapAtFive);
        register(event, env, "conversion_takes_base_ticks", 6100,
            MarineCompostGameTests::conversionTakesBaseTicks);
        register(event, env, "aeration_reduces_conversion_time", 5000,
            MarineCompostGameTests::aerationReducesConversionTime);
        register(event, env, "common_fish_yield", 6100,
            MarineCompostGameTests::commonFishYield);
        register(event, env, "rare_fish_yield", 6100,
            MarineCompostGameTests::rareFishYield);
        register(event, env, "legendary_fish_yield", 6100,
            MarineCompostGameTests::legendaryFishYield);
        register(event, env, "aeration_adds_to_yield", 5000,
            MarineCompostGameTests::aerationAddsToYield);
        register(event, env, "no_quality_defaults_to_common_yield", 6100,
            MarineCompostGameTests::noQualityDefaultsToCommonYield);
        register(event, env, "aeration_cooldown_blocks_too_early_attempt", 200,
            MarineCompostGameTests::aerationCooldownBlocksTooEarlyAttempt);
        register(event, env, "aeration_cooldown_allows_after_cooldown", 200,
            MarineCompostGameTests::aerationCooldownAllowsAfterCooldown);

        // ----- Fish Catch Data tests -----
        register(event, env, "recording_non_fish_is_ignored", 200,
            FishCatchDataGameTests::recordingNonFishIsIgnored);
        register(event, env, "recording_zero_size_is_ignored", 200,
            FishCatchDataGameTests::recordingZeroSizeIsIgnored);
        register(event, env, "catch_count_increments", 200,
            FishCatchDataGameTests::catchCountIncrements);
        register(event, env, "best_size_only_updates_on_improvement", 200,
            FishCatchDataGameTests::bestSizeOnlyUpdatesOnImprovement);
        register(event, env, "smaller_catch_does_not_override_best", 200,
            FishCatchDataGameTests::smallerCatchDoesNotOverrideBest);
        register(event, env, "personal_leaderboards_are_isolated_by_uuid", 200,
            FishCatchDataGameTests::personalLeaderboardsAreIsolatedByUuid);
        register(event, env, "global_best_size_tracks_highest_across_players", 200,
            FishCatchDataGameTests::globalBestSizeTracksHighestAcrossPlayers);
        register(event, env, "global_catch_count_sums_all_fish_types", 200,
            FishCatchDataGameTests::globalCatchCountSumsAllFishTypes);
        register(event, env, "personal_best_size_sort_order", 200,
            FishCatchDataGameTests::personalBestSizeSortOrder);
        register(event, env, "unknown_player_returns_empty", 200,
            FishCatchDataGameTests::unknownPlayerReturnsEmpty);
        register(event, env, "record_trash_contribution_accumulates_total", 200,
            helper -> FishCatchDataGameTests.recordTrashContributionAccumulatesTotal(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "record_trash_contribution_can_cross_multiple_thresholds_at_once", 200,
            helper -> FishCatchDataGameTests.recordTrashContributionCanCrossMultipleThresholdsAtOnce(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "record_trash_contribution_ignores_non_positive_amounts", 200,
            helper -> FishCatchDataGameTests.recordTrashContributionIgnoresNonPositiveAmounts(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "crossing_threshold_pays_out_tokens_proportionally", 200,
            helper -> FishCatchDataGameTests.crossingThresholdPaysOutTokensProportionally(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "reset_cleanup_goal_if_needed_wipes_contributions_on_new_week", 200,
            helper -> FishCatchDataGameTests.resetCleanupGoalIfNeededWipesContributionsOnNewWeek(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "get_cleanup_goal_contributors_lists_all_contributors", 200,
            helper -> FishCatchDataGameTests.getCleanupGoalContributorsListsAllContributors(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));

        // ----- Item Component tests -----
        register(event, env, "item_size_set_and_get", 200,
            ItemComponentGameTests::itemSizeSetAndGet);
        register(event, env, "item_size_remove", 200,
            ItemComponentGameTests::itemSizeRemove);
        register(event, env, "item_size_with_size_copies", 200,
            ItemComponentGameTests::itemSizeWithSizeCopies);
        register(event, env, "item_size_ignores_empty_stack", 200,
            ItemComponentGameTests::itemSizeIgnoresEmptyStack);
        register(event, env, "item_size_component_key", 200,
            ItemComponentGameTests::itemSizeComponentKey);
        register(event, env, "fish_quality_all_tiers", 200,
            ItemComponentGameTests::fishQualityAllTiers);
        register(event, env, "fish_quality_should_render_effect", 200,
            ItemComponentGameTests::fishQualityShouldRenderEffect);
        register(event, env, "fish_quality_effect_intensity", 200,
            ItemComponentGameTests::fishQualityEffectIntensity);
        register(event, env, "fish_quality_null_when_absent", 200,
            ItemComponentGameTests::fishQualityNullWhenAbsent);
        register(event, env, "bait_effect_worms_preset", 200,
            ItemComponentGameTests::baitEffectWormsPreset);
        register(event, env, "bait_effect_blazed_grub_exclusive_pool", 200,
            ItemComponentGameTests::baitEffectBlazedGrubExclusivePool);
        register(event, env, "bait_effect_component_round_trip", 200,
            ItemComponentGameTests::baitEffectComponentRoundTrip);
        register(event, env, "bait_effect_no_bait_defaults", 200,
            ItemComponentGameTests::baitEffectNoBaitDefaults);
        register(event, env, "bait_effect_trash_chance_presets", 200,
            ItemComponentGameTests::baitEffectTrashChancePresets);
        register(event, env, "bait_effect_trash_chance_component_round_trip", 200,
            ItemComponentGameTests::baitEffectTrashChanceComponentRoundTrip);
        register(event, env, "rod_bait_contents_empty_is_empty", 200,
            ItemComponentGameTests::rodBaitContentsEmptyIsEmpty);
        register(event, env, "rod_bait_contents_non_empty_stack_is_not_empty", 200,
            ItemComponentGameTests::rodBaitContentsNonEmptyStackIsNotEmpty);
        register(event, env, "rod_bait_contents_copy_stack_is_distinct", 200,
            ItemComponentGameTests::rodBaitContentsCopyStackIsDistinct);

        // ----- MathUtil / Utility tests -----
        register(event, env, "lerp_float_boundaries", 200,
            MathUtilGameTests::lerpFloatBoundaries);
        register(event, env, "lerp_double_boundaries", 200,
            MathUtilGameTests::lerpDoubleBoundaries);
        register(event, env, "clamp_float_bounds", 200,
            MathUtilGameTests::clampFloatBounds);
        register(event, env, "clamp_double_bounds", 200,
            MathUtilGameTests::clampDoubleBounds);
        register(event, env, "clamp_int_bounds", 200,
            MathUtilGameTests::clampIntBounds);
        register(event, env, "ease_in_out_quad_shape", 200,
            MathUtilGameTests::easeInOutQuadShape);
        register(event, env, "ease_out_cubic_shape", 200,
            MathUtilGameTests::easeOutCubicShape);
        register(event, env, "eased_lerp_identity_matches_plain_lerp", 200,
            MathUtilGameTests::easedLerpIdentityMatchesPlainLerp);
        register(event, env, "eased_lerp_applies_easing_function", 200,
            MathUtilGameTests::easedLerpAppliesEasingFunction);
        register(event, env, "utility_ft_creates_namespaced_identifier", 200,
            MathUtilGameTests::utilityFtCreatesNamespacedIdentifier);
        register(event, env, "utility_interpolate_color_boundaries", 200,
            MathUtilGameTests::utilityInterpolateColorBoundaries);

        // ----- FishingTarget tests -----
        register(event, env, "high_overlap_eventually_catches", 200,
            FishingTargetGameTests::highOverlapEventuallyCatches);
        register(event, env, "zero_overlap_eventually_fails", 200,
            FishingTargetGameTests::zeroOverlapEventuallyFails);
        register(event, env, "pick_random_boundary_rolls", 200,
            FishingTargetGameTests::pickRandomBoundaryRolls);
        register(event, env, "all_movement_patterns_tick_without_throwing", 200,
            FishingTargetGameTests::allMovementPatternsTickWithoutThrowing);
        register(event, env, "collection_animation_lifecycle", 200,
            FishingTargetGameTests::collectionAnimationLifecycle);
        register(event, env, "fail_animation_lifecycle", 200,
            FishingTargetGameTests::failAnimationLifecycle);

        // ----- PlayerQuestState tests -----
        register(event, env, "get_progress_defaults_for_untouched_quest", 200,
            PlayerQuestStateGameTests::getProgressDefaultsForUntouchedQuest);
        register(event, env, "increment_count_raises_count_and_flips_completed", 200,
            PlayerQuestStateGameTests::incrementCountRaisesCountAndFlipsCompleted);
        register(event, env, "can_claim_true_only_between_completion_and_claim", 200,
            PlayerQuestStateGameTests::canClaimTrueOnlyBetweenCompletionAndClaim);
        register(event, env, "claim_adds_tokens_without_resetting_count", 200,
            PlayerQuestStateGameTests::claimAddsTokensWithoutResettingCount);
        register(event, env, "reset_daily_if_needed_only_on_new_day", 200,
            PlayerQuestStateGameTests::resetDailyIfNeededOnlyOnNewDay);
        register(event, env, "purchase_fails_when_balance_too_low", 200,
            PlayerQuestStateGameTests::purchaseFailsWhenBalanceTooLow);
        register(event, env, "purchase_fails_when_max_purchases_reached", 200,
            PlayerQuestStateGameTests::purchaseFailsWhenMaxPurchasesReached);
        register(event, env, "purchase_zero_max_purchases_is_unlimited", 200,
            PlayerQuestStateGameTests::purchaseZeroMaxPurchasesIsUnlimited);
        register(event, env, "purchase_succeeds_deducts_and_increments_count", 200,
            PlayerQuestStateGameTests::purchaseSucceedsDeductsAndIncrementsCount);
        register(event, env, "reset_daily_purchases_if_needed_only_on_new_day", 200,
            PlayerQuestStateGameTests::resetDailyPurchasesIfNeededOnlyOnNewDay);
        register(event, env, "snapshots_reflect_mutations", 200,
            PlayerQuestStateGameTests::snapshotsReflectMutations);

        // ----- ItemEffect condition tests -----
        register(event, env, "item_tag_condition_matches_real_tag", 200,
            ItemEffectConditionGameTests::itemTagConditionMatchesRealTag);
        register(event, env, "component_condition_matches_presence_only", 200,
            ItemEffectConditionGameTests::componentConditionMatchesPresenceOnly);
        register(event, env, "component_value_condition_matches_field_value", 200,
            ItemEffectConditionGameTests::componentValueConditionMatchesFieldValue);
        register(event, env, "and_condition_semantics", 200,
            ItemEffectConditionGameTests::andConditionSemantics);
        register(event, env, "item_effect_matches_respects_enabled_and_conditions", 200,
            ItemEffectConditionGameTests::itemEffectMatchesRespectsEnabledAndConditions);

        // ----- Fish Tank tests -----
        register(event, env, "add_item_into_empty_tank_succeeds", 200,
            FishTankGameTests::addItemIntoEmptyTankSucceeds);
        register(event, env, "add_item_merges_into_existing_stack_before_new_slot", 200,
            FishTankGameTests::addItemMergesIntoExistingStackBeforeNewSlot);
        register(event, env, "add_item_fails_once_swarm_cap_reached", 200,
            FishTankGameTests::addItemFailsOnceSwarmCapReached);
        register(event, env, "extract_item_removes_last_slot_lifo_order", 200,
            FishTankGameTests::extractItemRemovesLastSlotLifoOrder);
        register(event, env, "first_item_rotation_reflects_slot_zero_insert", 200,
            FishTankGameTests::firstItemRotationReflectsSlotZeroInsert);
        register(event, env, "cosmetics_round_trip", 200,
            FishTankGameTests::cosmeticsRoundTrip);
        register(event, env, "open_faces_round_trip", 200,
            FishTankGameTests::openFacesRoundTrip);

        // ----- TutorialManager tests -----
        register(event, env, "crafting_rod_from_default_step_grants_worms_and_advances", 200,
            helper -> TutorialManagerGameTests.craftingRodFromDefaultStepGrantsWormsAndAdvances(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "crafting_rod_again_after_advancing_is_no_op", 200,
            helper -> TutorialManagerGameTests.craftingRodAgainAfterAdvancingIsNoOp(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "on_bait_loaded_only_advances_from_bait_load_step", 200,
            helper -> TutorialManagerGameTests.onBaitLoadedOnlyAdvancesFromBaitLoadStep(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "on_hook_cast_only_advances_from_castable_steps", 200,
            helper -> TutorialManagerGameTests.onHookCastOnlyAdvancesFromCastableSteps(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "advance_step_no_op_when_from_step_does_not_match_current", 200,
            helper -> TutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "tutorial_walks_full_documented_chain_to_completion", 200,
            helper -> TutorialManagerGameTests.tutorialWalksFullDocumentedChainToCompletion(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "on_quest_claimed_only_advances_on_matching_tutorial_quest_id", 200,
            helper -> TutorialManagerGameTests.onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "crafting_rod_via_shift_click_from_result_slot_advances_to_bait_load", 200,
            helper -> TutorialManagerGameTests.craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "crafting_rod_via_simple_click_from_result_slot_advances_to_bait_load", 200,
            helper -> TutorialManagerGameTests.craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "loading_bait_via_left_click_in_inventory_screen_advances_to_waiting_for_cast", 200,
            helper -> TutorialManagerGameTests.loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "revoking_rod_advancement_allows_re_triggering_after_reset", 200,
            helper -> TutorialManagerGameTests.revokingRodAdvancementAllowsReTriggeringAfterReset(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));

        // ----- EncyclopediaTutorialManager tests -----
        register(event, env, "opening_encyclopedia_from_not_started_starts_intro", 200,
            helper -> EncyclopediaTutorialManagerGameTests.openingEncyclopediaFromNotStartedStartsIntro(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "reopening_encyclopedia_while_in_progress_is_idempotent", 200,
            helper -> EncyclopediaTutorialManagerGameTests.reopeningEncyclopediaWhileInProgressIsIdempotent(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "opening_encyclopedia_after_complete_does_not_restart", 200,
            helper -> EncyclopediaTutorialManagerGameTests.openingEncyclopediaAfterCompleteDoesNotRestart(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "encyclopedia_advance_step_no_op_when_from_step_does_not_match_current", 200,
            helper -> EncyclopediaTutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "encyclopedia_tutorial_walks_full_chain_to_completion", 200,
            helper -> EncyclopediaTutorialManagerGameTests.encyclopediaTutorialWalksFullChainToCompletion(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));

        // ----- QuestTracker tests -----
        register(event, env, "target_species_gates_match_when_present", 200,
            QuestTrackerGameTests::targetSpeciesGatesMatchWhenPresent);
        register(event, env, "target_species_tag_gates_match_when_present", 200,
            QuestTrackerGameTests::targetSpeciesTagGatesMatchWhenPresent);
        register(event, env, "min_quality_is_ordinal_floor", 200,
            QuestTrackerGameTests::minQualityIsOrdinalFloor);
        register(event, env, "biome_condition_gates_match_when_present", 200,
            QuestTrackerGameTests::biomeConditionGatesMatchWhenPresent);
        register(event, env, "time_condition_gates_match_when_present", 200,
            QuestTrackerGameTests::timeConditionGatesMatchWhenPresent);
        register(event, env, "weather_condition_gates_match_when_present", 200,
            QuestTrackerGameTests::weatherConditionGatesMatchWhenPresent);
        register(event, env, "zone_condition_gates_match_when_present", 200,
            QuestTrackerGameTests::zoneConditionGatesMatchWhenPresent);
        register(event, env, "min_size_is_floor", 200,
            QuestTrackerGameTests::minSizeIsFloor);
        register(event, env, "min_session_catches_does_not_affect_per_stack_matching", 200,
            QuestTrackerGameTests::minSessionCatchesDoesNotAffectPerStackMatching);
        register(event, env, "all_conditions_must_match_together", 200,
            QuestTrackerGameTests::allConditionsMustMatchTogether);
        register(event, env, "get_active_dailies_is_stable_per_day", 200,
            QuestTrackerGameTests::getActiveDailiesIsStablePerDay);
        register(event, env, "get_active_dailies_never_exceeds_cap_and_excludes_non_daily", 200,
            QuestTrackerGameTests::getActiveDailiesNeverExceedsCapAndExcludesNonDaily);
        register(event, env, "get_active_dailies_caps_at_registry_size_when_smaller_than_count", 200,
            QuestTrackerGameTests::getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount);

        // ----- Packet round-trip tests -----
        register(event, env, "start_fishing_minigame_packet_round_trips", 200,
            PacketRoundTripGameTests::startFishingMinigamePacketRoundTrips);
        register(event, env, "purchase_shop_entry_packet_round_trips", 200,
            PacketRoundTripGameTests::purchaseShopEntryPacketRoundTrips);
        register(event, env, "quest_sync_packet_round_trips", 200,
            PacketRoundTripGameTests::questSyncPacketRoundTrips);
        register(event, env, "quest_sync_packet_cleanup_goal_milestone_round_trips", 200,
            PacketRoundTripGameTests::questSyncPacketCleanupGoalMilestoneRoundTrips);
        register(event, env, "fish_encyclopedia_sync_packet_round_trips", 200,
            PacketRoundTripGameTests::fishEncyclopediaSyncPacketRoundTrips);
        register(event, env, "request_fish_encyclopedia_packet_round_trips", 200,
            PacketRoundTripGameTests::requestFishEncyclopediaPacketRoundTrips);

        // ----- FishEncyclopediaEntry tests -----
        register(event, env, "empty_object_decodes_to_all_defaults", 200,
            FishEncyclopediaEntryGameTests::emptyObjectDecodesToAllDefaults);
        register(event, env, "partial_thresholds_fill_remaining_defaults", 200,
            FishEncyclopediaEntryGameTests::partialThresholdsFillRemainingDefaults);
        register(event, env, "full_entry_round_trips_through_json", 200,
            FishEncyclopediaEntryGameTests::fullEntryRoundTripsThroughJson);

        // ----- FishEncyclopediaClientCache / FishEncyclopediaClientHelper tests -----
        register(event, env, "cache_starts_empty", 200,
            FishEncyclopediaClientGameTests::cacheStartsEmpty);
        register(event, env, "update_populates_catch_counts_by_fish_type", 200,
            FishEncyclopediaClientGameTests::updatePopulatesCatchCountsByFishType);
        register(event, env, "update_indexes_best_sizes_by_fish_type", 200,
            FishEncyclopediaClientGameTests::updateIndexesBestSizesByFishType);
        register(event, env, "update_replaces_prior_contents_rather_than_merging", 200,
            FishEncyclopediaClientGameTests::updateReplacesPriorContentsRatherThanMerging);
        register(event, env, "reset_clears_all_maps", 200,
            FishEncyclopediaClientGameTests::resetClearsAllMaps);
        register(event, env, "get_encyclopedia_entry_falls_back_to_default_for_unregistered_fish", 200,
            FishEncyclopediaClientGameTests::getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish);
        register(event, env, "get_all_fish_profiles_sorted_matches_registry_entry_set", 200,
            FishEncyclopediaClientGameTests::getAllFishProfilesSortedMatchesRegistryEntrySet);

        // ----- FishingMinigameManager tests -----
        register(event, env, "start_session_end_to_end_returns_valid_session_id", 200,
            helper -> FishingMinigameManagerGameTests.startSessionEndToEndReturnsValidSessionId(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "start_session_when_already_active_returns_negative_one_unless_cancelled", 200,
            helper -> FishingMinigameManagerGameTests.startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "cancel_session_removes_active_session", 200,
            helper -> FishingMinigameManagerGameTests.cancelSessionRemovesActiveSession(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "handle_minigame_complete_awards_only_rewards_for_valid_indices_and_ignores_others", 200,
            helper -> FishingMinigameManagerGameTests.handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "handle_minigame_complete_is_no_op_for_unknown_or_mismatched_session", 200,
            helper -> FishingMinigameManagerGameTests.handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "handle_minigame_complete_session_is_single_use_even_when_indices_are_invalid", 200,
            helper -> FishingMinigameManagerGameTests.handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "handle_minigame_complete_grants_rewards_even_when_completed_in_under_twenty_ticks", 200,
            helper -> FishingMinigameManagerGameTests.handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "handle_minigame_complete_consumes_bait_only_when_rewards_were_actually_awarded", 200,
            helper -> FishingMinigameManagerGameTests.handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "trash_chance_one_always_awards_trash_items", 200,
            helper -> FishingMinigameManagerGameTests.trashChanceOneAlwaysAwardsTrashItems(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
        register(event, env, "treasure_chance_one_with_zero_trash_never_awards_trash", 200,
            helper -> FishingMinigameManagerGameTests.treasureChanceOneWithZeroTrashNeverAwardsTrash(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));

        // ----- ShopEntry tests -----
        register(event, env, "get_active_daily_shop_is_stable_per_day", 200,
            ShopEntryGameTests::getActiveDailyShopIsStablePerDay);
        register(event, env, "get_active_daily_shop_never_exceeds_cap", 200,
            ShopEntryGameTests::getActiveDailyShopNeverExceedsCap);
        register(event, env, "get_active_daily_shop_caps_at_registry_size_when_smaller_than_count", 200,
            ShopEntryGameTests::getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount);
        register(event, env, "get_active_daily_shop_weight_biases_selection_toward_heavier_entries", 200,
            ShopEntryGameTests::getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries);
        register(event, env, "get_active_daily_shop_handles_non_positive_weight_without_error", 200,
            ShopEntryGameTests::getActiveDailyShopHandlesNonPositiveWeightWithoutError);
        register(event, env, "shop_entry_codec_defaults_weight_to_one_when_absent", 200,
            ShopEntryGameTests::shopEntryCodecDefaultsWeightToOneWhenAbsent);
        register(event, env, "get_active_daily_shop_charm_replacement_rate_matches_configured_chance", 200,
            ShopEntryGameTests::getActiveDailyShopCharmReplacementRateMatchesConfiguredChance);
        register(event, env, "get_active_daily_shop_never_replaces_without_a_charm_pool", 200,
            ShopEntryGameTests::getActiveDailyShopNeverReplacesWithoutACharmPool);
        register(event, env, "get_active_daily_shop_handles_empty_main_pool_with_charms_only", 200,
            ShopEntryGameTests::getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly);

        register(event, env, "display_count_clamps_overshoot_to_the_target", 200,
            PlayerQuestStateGameTests::displayCountClampsOvershootToTheTarget);

        // ----- Storm Charm -----
        register(event, env, "storm_charm_is_slottable_into_the_rod", 200,
            StormCharmGameTests::stormCharmIsSlottableIntoTheRod);
        register(event, env, "storm_charm_carries_no_charm_effect", 200,
            StormCharmGameTests::stormCharmCarriesNoCharmEffect);
        register(event, env, "storm_charm_stacks_unlike_rod_charms", 200,
            StormCharmGameTests::stormCharmStacksUnlikeRodCharms);
        register(event, env, "summoned_storm_is_read_as_thunder_by_quest_conditions", 200,
            StormCharmGameTests::summonedStormIsReadAsThunderByQuestConditions);
        register(event, env, "hand_use_charges_up_and_is_free_to_cancel", 200,
            StormCharmGameTests::handUseChargesUpAndIsFreeToCancel);
        register(event, env, "storm_duration_is_within_vanilla_thunder_range", 200,
            StormCharmGameTests::stormDurationIsWithinVanillaThunderRange);

        // ----- Quest content validation -----
        register(event, env, "every_target_species_quest_is_satisfiable", 200,
            QuestSatisfiabilityGameTests::everyTargetSpeciesQuestIsSatisfiable);
        register(event, env, "every_prerequisite_resolves", 200,
            QuestSatisfiabilityGameTests::everyPrerequisiteResolves);
        register(event, env, "no_prerequisite_cycles", 200,
            QuestSatisfiabilityGameTests::noPrerequisiteCycles);
        register(event, env, "daily_pool_is_larger_than_the_draw_and_actually_rotates", 200,
            QuestSatisfiabilityGameTests::dailyPoolIsLargerThanTheDrawAndActuallyRotates);
        register(event, env, "lifetime_quests_carry_no_unreplayable_conditions", 200,
            QuestSatisfiabilityGameTests::lifetimeQuestsCarryNoUnreplayableConditions);

        // ----- Quest log visibility -----
        register(event, env, "non_hidden_quest_is_always_listed", 200,
            QuestLogVisibilityGameTests::nonHiddenQuestIsAlwaysListed);
        register(event, env, "hidden_secret_stays_out_until_completed", 200,
            QuestLogVisibilityGameTests::hiddenSecretStaysOutUntilCompleted);
        register(event, env, "hidden_chain_quest_appears_once_prerequisite_claimed", 200,
            QuestLogVisibilityGameTests::hiddenChainQuestAppearsOncePrerequisiteClaimed);
        register(event, env, "completed_chain_quest_is_listed_even_if_prerequisite_unclaimed", 200,
            QuestLogVisibilityGameTests::completedChainQuestIsListedEvenIfPrerequisiteUnclaimed);

        // ----- Lifetime quest progress -----
        register(event, env, "catch_count_matching_scopes_to_the_requested_species", 200,
            LifetimeQuestProgressGameTests::catchCountMatchingScopesToTheRequestedSpecies);
        register(event, env, "lifetime_counts_are_isolated_per_player", 200,
            LifetimeQuestProgressGameTests::lifetimeCountsAreIsolatedPerPlayer);
        register(event, env, "one_lifetime_total_satisfies_every_tier_it_has_passed", 200,
            LifetimeQuestProgressGameTests::oneLifetimeTotalSatisfiesEveryTierItHasPassed);
        register(event, env, "non_fish_catches_do_not_advance_lifetime_chains", 200,
            LifetimeQuestProgressGameTests::nonFishCatchesDoNotAdvanceLifetimeChains);
        register(event, env, "lifetime_compatibility_rejects_environmental_conditions", 200,
            LifetimeQuestProgressGameTests::lifetimeCompatibilityRejectsEnvironmentalConditions);

        // ----- Capstone rewards / unlock-gated shop -----
        register(event, env, "gated_entries_are_absent_until_their_quest_is_claimed", 200,
            CapstoneRewardGameTests::gatedEntriesAreAbsentUntilTheirQuestIsClaimed);
        register(event, env, "gated_entries_can_appear_once_their_quest_is_claimed", 200,
            CapstoneRewardGameTests::gatedEntriesCanAppearOnceTheirQuestIsClaimed);
        register(event, env, "locked_entries_do_not_consume_shop_slots", 200,
            CapstoneRewardGameTests::lockedEntriesDoNotConsumeShopSlots);
        register(event, env, "gated_entry_sells_the_same_item_its_quest_granted", 200,
            CapstoneRewardGameTests::gatedEntrySellsTheSameItemItsQuestGranted);
        register(event, env, "unlock_gates_never_point_at_daily_quests", 200,
            CapstoneRewardGameTests::unlockGatesNeverPointAtDailyQuests);
        register(event, env, "capstone_tanks_carry_their_materials_component", 200,
            CapstoneRewardGameTests::capstoneTanksCarryTheirMaterialsComponent);

        // ----- Creative tab tests -----
        register(event, env, "decorations_tab_contains_exactly_cosmetic_structures_and_decorations", 200,
            CreativeTabGameTests::decorationsTabContainsExactlyCosmeticStructuresAndDecorations);
        register(event, env, "main_tab_no_longer_contains_moved_cosmetics", 200,
            CreativeTabGameTests::mainTabNoLongerContainsMovedCosmetics);

        register(event, env, "advance_step_bait_load_transitions_to_waiting_for_cast", 200,
            helper -> TutorialManagerGameTests.advanceStepBaitLoadTransitionsToWaitingForCast(helper, () -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)));
    }

    private static void register(
        RegisterGameTestsEvent event,
        Holder<TestEnvironmentDefinition<?>> env,
        String name,
        int maxTicks,
        Consumer<GameTestHelper> test
    ) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data =
            new TestData<>(env, EMPTY_STRUCTURE, maxTicks, 0, true);
        event.registerTest(
            Identifier.fromNamespaceAndPath(MOD_ID, name),
            new ConsumerTestInstance(test, data)
        );
    }

    /** Minimal GameTestInstance that wraps a Consumer<GameTestHelper>. */
    private static final class ConsumerTestInstance extends GameTestInstance {

        private final Consumer<GameTestHelper> fn;

        ConsumerTestInstance(Consumer<GameTestHelper> fn, TestData<Holder<TestEnvironmentDefinition<?>>> data) {
            super(data);
            this.fn = fn;
        }

        @Override
        public void run(GameTestHelper helper) {
            fn.accept(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            // Registries.TEST_INSTANCE is a synchronized registry, so this WOULD be called to send
            // entries to connecting clients if they were ever registered outside a runGametest run.
            // The neoforge.enabledGameTestNamespaces gate in onRegisterGameTests keeps that from happening.
            throw new UnsupportedOperationException("ConsumerTestInstance is not serializable");
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("fishtastic");
        }
    }
}
