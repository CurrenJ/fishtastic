package grill24.fishtastic.neoforge.gametest;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.gametest.FishCatchDataGameTests;
import grill24.fishtastic.gametest.ItemComponentGameTests;
import grill24.fishtastic.gametest.WormBinGameTests;
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
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(MOD_ID, "default"),
            new TestEnvironmentDefinition.AllOf(java.util.List.of())
        );

        // ----- Worm Bin tests -----
        register(event, env, "worm_bin_starts_empty", 200,
            WormBinGameTests::wormBinStartsEmpty);
        register(event, env, "deposit_fish_adds_to_list", 200,
            WormBinGameTests::depositFishAddsToList);
        register(event, env, "deposit_cap_at_five", 200,
            WormBinGameTests::depositCapAtFive);
        register(event, env, "aeration_cap_at_five", 200,
            WormBinGameTests::aerationCapAtFive);
        register(event, env, "conversion_takes_base_ticks", 6100,
            WormBinGameTests::conversionTakesBaseTicks);
        register(event, env, "aeration_reduces_conversion_time", 5000,
            WormBinGameTests::aerationReducesConversionTime);
        register(event, env, "common_fish_yield", 6100,
            WormBinGameTests::commonFishYield);
        register(event, env, "legendary_fish_yield", 6100,
            WormBinGameTests::legendaryFishYield);
        register(event, env, "aeration_adds_to_yield", 5000,
            WormBinGameTests::aerationAddsToYield);
        register(event, env, "mixed_quality_yield", 6100,
            WormBinGameTests::mixedQualityYield);
        register(event, env, "harvest_resets_to_empty", 6100,
            WormBinGameTests::harvestResetsToEmpty);
        register(event, env, "deposited_fish_list_retains_order", 200,
            WormBinGameTests::depositedFishListRetainsOrder);

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
            // Only called for JSON serialization — never needed for programmatic registration.
            throw new UnsupportedOperationException("ConsumerTestInstance is not serializable");
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("fishtastic");
        }
    }
}
