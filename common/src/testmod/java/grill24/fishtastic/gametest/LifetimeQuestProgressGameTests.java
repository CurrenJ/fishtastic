package grill24.fishtastic.gametest;

import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.UUID;

/**
 * Covers the lifetime-count backing for tiered mastery objectives.
 *
 * <p>Regression guard for the pre-release behaviour where each tier kept its own counter starting
 * at 0 and only began counting once the previous tier was <em>claimed</em>. That made a
 * "Catch 10 / 25 / 50 X total" chain actually cost 85 catches, and silently discarded every catch
 * made between finishing a tier and getting round to claiming it.
 */
public final class LifetimeQuestProgressGameTests {

    private LifetimeQuestProgressGameTests() {}

    private static ItemStack fish(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        ItemSizeHelper.setSize(stack, 42.0f);
        FishQualityHelper.setQuality(stack, FishQuality.Quality.COMMON);
        return stack;
    }

    private static QuestObjective lifetimeObjective(Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.item.Item>> species,
                                                     Optional<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> tag,
                                                     int target) {
        return new QuestObjective(species, tag, Optional.empty(), false, Optional.of(target),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 1, true);
    }

    /** Per-species lifetime totals accumulate across catches and stay isolated per species. */
    public static void catchCountMatchingScopesToTheRequestedSpecies(GameTestHelper helper) {
        FishCatchSavedData data = new FishCatchSavedData();
        UUID player = UUID.randomUUID();

        for (int i = 0; i < 7; i++) data.recordCatch(player, "tester", fish(Items.COD));
        for (int i = 0; i < 3; i++) data.recordCatch(player, "tester", fish(Items.SALMON));

        Identifier cod = Identifier.withDefaultNamespace("cod");
        Identifier salmon = Identifier.withDefaultNamespace("salmon");

        helper.assertTrue(data.getCatchCount(player, cod) == 7,
                "Cod lifetime count must be 7, was " + data.getCatchCount(player, cod));
        helper.assertTrue(data.getCatchCountMatching(player, id -> id.equals(cod)) == 7,
                "Filtered lifetime count must isolate cod");
        helper.assertTrue(data.getCatchCountMatching(player, id -> id.equals(salmon)) == 3,
                "Filtered lifetime count must isolate salmon");
        helper.assertTrue(data.getTotalCatchCount(player) == 10,
                "Total lifetime count must sum every species, was " + data.getTotalCatchCount(player));
        helper.succeed();
    }

    /** Lifetime totals are per-player — one player's catches never leak into another's chain. */
    public static void lifetimeCountsAreIsolatedPerPlayer(GameTestHelper helper) {
        FishCatchSavedData data = new FishCatchSavedData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        for (int i = 0; i < 5; i++) data.recordCatch(first, "first", fish(Items.COD));
        data.recordCatch(second, "second", fish(Items.COD));

        helper.assertTrue(data.getTotalCatchCount(first) == 5, "First player must have 5 lifetime catches");
        helper.assertTrue(data.getTotalCatchCount(second) == 1, "Second player must have 1 lifetime catch");
        helper.assertTrue(data.getTotalCatchCount(UUID.randomUUID()) == 0,
                "An unknown player must have no lifetime catches");
        helper.succeed();
    }

    /**
     * The point of the change: a single running total simultaneously satisfies every tier it has
     * passed, instead of each tier restarting from zero. 50 lifetime catches completes the 10/25/50
     * chain outright — it does not cost 10 + 25 + 50.
     */
    public static void oneLifetimeTotalSatisfiesEveryTierItHasPassed(GameTestHelper helper) {
        FishCatchSavedData data = new FishCatchSavedData();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 50; i++) data.recordCatch(player, "tester", fish(Items.COD));

        int lifetime = data.getCatchCount(player, Identifier.withDefaultNamespace("cod"));

        helper.assertTrue(lifetime >= 10, "Tier 1 (10) must be satisfied by 50 lifetime catches");
        helper.assertTrue(lifetime >= 25, "Tier 2 (25) must be satisfied by the same 50 lifetime catches");
        helper.assertTrue(lifetime >= 50, "Tier 3 (50) must be satisfied by the same 50 lifetime catches");
        helper.assertTrue(lifetime == 50,
                "The chain must cost 50 catches total, not 10+25+50 — was " + lifetime);
        helper.succeed();
    }

    /**
     * Only fish are recorded, so trash and treasure pulled from the same cast never inflate an
     * "N fish total" chain.
     */
    public static void nonFishCatchesDoNotAdvanceLifetimeChains(GameTestHelper helper) {
        FishCatchSavedData data = new FishCatchSavedData();
        UUID player = UUID.randomUUID();

        data.recordCatch(player, "tester", fish(Items.COD));
        data.recordCatch(player, "tester", new ItemStack(Items.SADDLE));
        data.recordCatch(player, "tester", new ItemStack(Items.LEATHER_BOOTS));

        helper.assertTrue(data.getTotalCatchCount(player) == 1,
                "Only the fish must count toward a lifetime chain, was " + data.getTotalCatchCount(player));
        helper.succeed();
    }

    /**
     * A lifetime objective must not carry conditions that lifetime records can't express — catch
     * data stores species and counts, not the biome/time/weather a fish was landed in.
     */
    public static void lifetimeCompatibilityRejectsEnvironmentalConditions(GameTestHelper helper) {
        QuestObjective plainSpecies = lifetimeObjective(Optional.empty(), Optional.of(ItemTags.FISHES), 50);
        helper.assertTrue(plainSpecies.isLifetimeCompatible(),
                "A plain species/tag count objective must be lifetime-compatible");

        QuestObjective withQuality = new QuestObjective(Optional.empty(), Optional.of(ItemTags.FISHES),
                Optional.empty(), false, Optional.of(50), Optional.of(FishQuality.Quality.RARE),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 1, true);
        helper.assertFalse(withQuality.isLifetimeCompatible(),
                "min_quality can't be replayed against lifetime records");

        QuestObjective withTime = new QuestObjective(Optional.empty(), Optional.of(ItemTags.FISHES),
                Optional.empty(), false, Optional.of(50), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(grill24.fishtastic.data.FishProfile.TimeOfDay.NIGHT),
                Optional.empty(), Optional.empty(), Optional.empty(), 1, true);
        helper.assertFalse(withTime.isLifetimeCompatible(),
                "time_condition can't be replayed against lifetime records");
        helper.succeed();
    }
}
