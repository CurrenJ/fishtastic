package grill24.fishtastic.server;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the shared fishing pool resolver (Phase 3). Every expected number in here was produced by the
 * pre-refactor implementation and captured before the extraction: the characterization dump it was
 * diffed against lives at {@code build/phase3-characterization-before.txt} and all fifteen
 * scenarios it covered are reproduced by the tests below (pool order, per-candidate weights,
 * top-three ranking and seeded draws — the seed pin replays every scenario's 12 draws). If a value
 * here disagrees with the resolver, the resolver changed behaviour — that is the point.
 *
 * <p>Headless: {@link FishingPoolFixture} bootstraps the minimum registry state needed. Anything
 * world- or player-dependent (real hooks, real inventories, environment sampling) is covered by the
 * GameTests in {@code FishingMinigameManagerGameTests} instead.
 */
class FishingPoolResolverTest {

    static final MappedRegistry<Item> ITEMS = FishingPoolFixture.items();
    static final MappedRegistry<FishProfile> PROFILES = FishingPoolFixture.profiles();

    static {
        FishingPoolFixture.ensureDataComponents();
    }

    static final String COD = "minecraft:cod";
    static final String SALMON = "minecraft:salmon";
    static final String TROPICAL_FISH = "minecraft:tropical_fish";
    static final String PUFFERFISH = "minecraft:pufferfish";
    static final String DARTER = "fishtastic:river_darter";
    static final String OCEAN_GLIDER = "fishtastic:ocean_glider";
    static final String BLENNY = "fishtastic:exotic_blenny";
    static final String CYPRINID = "fishtastic:colorful_cyprinid";
    static final String IAPSIS = "fishtastic:diamond_alert_iapsis";
    static final String MINNOW = "fishtastic:profileless_minnow";
    static final String POOLFRY = "fishtastic:poolfry";

    // ---- the characterization scenarios, verbatim ----

    static final FishingPoolResolver.EnvironmentContext RIVER_DAY =
            env(FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, FishMoonPhase.NEW_MOON,
                    FishingPoolFixture.RIVER_ZONES);
    static final FishingPoolResolver.EnvironmentContext RIVER_DAY_RAIN =
            env(FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.RAIN, FishMoonPhase.NEW_MOON,
                    FishingPoolFixture.RIVER_ZONES);
    static final FishingPoolResolver.EnvironmentContext RIVER_DAY_FULL_MOON =
            env(FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, FishMoonPhase.FULL_MOON,
                    FishingPoolFixture.RIVER_ZONES);
    static final FishingPoolResolver.EnvironmentContext RIVER_DUSK_RAIN =
            env(FishProfile.TimeOfDay.DUSK, FishProfile.WeatherCondition.RAIN, FishMoonPhase.NEW_MOON,
                    FishingPoolFixture.RIVER_ZONES);
    static final FishingPoolResolver.EnvironmentContext RIVER_NIGHT =
            env(FishProfile.TimeOfDay.NIGHT, FishProfile.WeatherCondition.CLEAR, FishMoonPhase.FULL_MOON,
                    FishingPoolFixture.RIVER_ZONES);
    static final FishingPoolResolver.EnvironmentContext OCEAN_NIGHT =
            env(FishProfile.TimeOfDay.NIGHT, FishProfile.WeatherCondition.CLEAR, FishMoonPhase.FULL_MOON,
                    FishingPoolFixture.OCEAN_ZONES);
    static final FishingPoolResolver.EnvironmentContext RIVER_HIGH_ALTITUDE =
            env(FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, FishMoonPhase.NEW_MOON,
                    FishingPoolFixture.HIGH_ALTITUDE_ZONES);

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static FishingPoolResolver.EnvironmentContext env(FishProfile.TimeOfDay time,
                                                      FishProfile.WeatherCondition weather,
                                                      FishMoonPhase moon,
                                                      Set<FishProfile.Zone> zones) {
        return new FishingPoolResolver.EnvironmentContext(FishingPoolFixture.riverBiome(), time, weather, moon, zones);
    }

    static FishingPoolResolver.SpeciesContext resolve(FishingPoolResolver.EnvironmentContext environment,
                                                      FishingPoolResolver.Inputs inputs) {
        return FishingPoolResolver.resolve(ITEMS, PROFILES, environment, inputs);
    }

    /** Standard-worms baseline: the mode a player with no rod held (or no bait loaded) resolves in. */
    static FishingPoolResolver.SpeciesContext baseline(FishingPoolResolver.EnvironmentContext environment) {
        return resolve(environment, FishingPoolResolver.Inputs.standardWormsBaseline());
    }

    static FishingPoolResolver.SpeciesContext bait(FishingPoolResolver.EnvironmentContext environment,
                                                   BaitEffect baitEffect, CharmEffect charmEffect) {
        return resolve(environment, FishingPoolResolver.Inputs.of(baitEffect, charmEffect,
                FishingPoolResolver.PersonalEquipment.NEUTRAL));
    }

    static List<String> ids(List<FishingPoolResolver.WeightedFish> candidates) {
        return candidates.stream().map(candidate -> FishingPoolFixture.idOf(candidate.item())).toList();
    }

    static List<Integer> weights(List<FishingPoolResolver.WeightedFish> candidates) {
        return candidates.stream().map(FishingPoolResolver.WeightedFish::weight).toList();
    }

    static Map<String, Integer> weightById(FishingPoolResolver.SpeciesContext context) {
        Map<String, Integer> byId = new LinkedHashMap<>();
        for (FishingPoolResolver.WeightedFish candidate : context.candidates()) {
            byId.put(FishingPoolFixture.idOf(candidate.item()), candidate.weight());
        }
        return byId;
    }

    static List<String> topIds(FishingPoolResolver.SpeciesContext context, int count) {
        return ids(FishingPoolResolver.topWeighted(context, count));
    }

    // -------------------------------------------------------------------------
    // Baseline mode: pool order, final weights, ranking
    // -------------------------------------------------------------------------

    @Test
    void standardWormsBaselinePinsPoolOrderWeightsAndRanking() {
        FishingPoolResolver.SpeciesContext context = baseline(RIVER_DAY);

        assertEquals(List.of(DARTER, BLENNY, CYPRINID, IAPSIS, MINNOW, POOLFRY), ids(context.candidates()),
                "candidate order must be the fishes-tag order after filtering — vanilla excluded under any bait");
        assertEquals(List.of(20, 12, 8, 6, 10, 8), weights(context.candidates()));
        assertEquals(List.of(DARTER, BLENNY, MINNOW), topIds(context, 3));
        assertEquals(RIVER_DAY, context.environment());
    }

    @Test
    void baselineModeCarriesNeutralPersonalEquipment() {
        // The baseline is neutral equipment by construction — it never reads a player's inventory,
        // so an inventory full of diamonds cannot leak into a no-rod ambient preview.
        assertEquals(0, FishingPoolResolver.Inputs.standardWormsBaseline().personalEquipment().inventoryDiamonds());
        assertEquals(FishingPoolResolver.PersonalEquipment.NEUTRAL,
                FishingPoolResolver.Inputs.standardWormsBaseline().personalEquipment());

        assertEquals(baseline(RIVER_DAY),
                resolve(RIVER_DAY, FishingPoolResolver.Inputs.of(BaitEffect.WORMS, null,
                        FishingPoolResolver.PersonalEquipment.NEUTRAL)),
                "the baseline is exactly WORMS under neutral personal equipment");
    }

    @Test
    void zoneGateAndEnvironmentSamplingChangeThePool() {
        FishingPoolResolver.SpeciesContext ocean = baseline(OCEAN_NIGHT);
        assertEquals(List.of(OCEAN_GLIDER, IAPSIS, MINNOW), ids(ocean.candidates()));
        assertEquals(List.of(90, 6, 10), weights(ocean.candidates()));

        // High altitude is an extra zone, not an extra multiplier for these species: same pool and
        // weights as plain river, but the environment (and so the fingerprint) still differs.
        FishingPoolResolver.SpeciesContext high = baseline(RIVER_HIGH_ALTITUDE);
        assertEquals(ids(baseline(RIVER_DAY).candidates()), ids(high.candidates()));
        assertEquals(weights(baseline(RIVER_DAY).candidates()), weights(high.candidates()));
        assertNotEquals(baseline(RIVER_DAY).fingerprint(), high.fingerprint(),
                "zones are a species-affecting input even when they do not move a weight today");
    }

    // -------------------------------------------------------------------------
    // No-bait mode: vanilla stays in the pool with pinned weights
    // -------------------------------------------------------------------------

    @Test
    void noBaitKeepsVanillaInterleavedAndPinsVanillaWeights() {
        FishingPoolResolver.SpeciesContext context = bait(RIVER_DAY, BaitEffect.NO_BAIT, null);

        assertEquals(List.of(COD, DARTER, SALMON, BLENNY, CYPRINID, IAPSIS, MINNOW, TROPICAL_FISH, POOLFRY, PUFFERFISH),
                ids(context.candidates()));
        assertEquals(List.of(200, 3, 200, 1, 1, 1, 1, 50, 1, 25), weights(context.candidates()));
        assertEquals(List.of(COD, SALMON, TROPICAL_FISH), topIds(context, 3));
    }

    @Test
    void nullBaitKeepsVanillaAndLeavesModFishUnscaled() {
        FishingPoolResolver.SpeciesContext context = bait(RIVER_DAY, null, null);

        assertEquals(List.of(COD, DARTER, SALMON, BLENNY, CYPRINID, IAPSIS, MINNOW, TROPICAL_FISH, POOLFRY, PUFFERFISH),
                ids(context.candidates()));
        // Same vanilla weights as NO_BAIT (200/200/50/25 — vanilla never gets modFishMultiplier),
        // but mod fish keep their full weight instead of NO_BAIT's 0.15 scalar.
        assertEquals(List.of(200, 20, 200, 12, 8, 6, 10, 50, 8, 25), weights(context.candidates()));
    }

    @Test
    void noBaitNightLunaPinsNightMultiplierOnBothBranches() {
        FishingPoolResolver.SpeciesContext context = bait(RIVER_NIGHT, BaitEffect.NO_BAIT, CharmEffect.LUNA_CHARM);

        Map<String, Integer> byId = weightById(context);
        assertEquals(250, byId.get(COD).intValue());
        assertEquals(250, byId.get(SALMON).intValue());
        assertEquals(62, byId.get(TROPICAL_FISH).intValue());
        assertEquals(31, byId.get(PUFFERFISH).intValue());
        assertEquals(2, byId.get(BLENNY).intValue());
        assertEquals(1, byId.get(DARTER).intValue());
        assertEquals(1, byId.get(CYPRINID).intValue());
        assertEquals(1, byId.get(IAPSIS).intValue());
        assertEquals(1, byId.get(MINNOW).intValue());
        assertEquals(1, byId.get(POOLFRY).intValue());
    }

    // -------------------------------------------------------------------------
    // Environment and charm multipliers on the mod branch
    // -------------------------------------------------------------------------

    @Test
    void duskRainMultipliesRiverDarterAndLeavesOthers() {
        FishingPoolResolver.SpeciesContext context = baseline(RIVER_DUSK_RAIN);

        assertEquals(List.of(80, 12, 8, 6, 10, 8), weights(context.candidates()),
                "dusk (x2) and rain (x2) are both PRIMARY for river darter, everything else is untouched");
    }

    @Test
    void lunaCharmRaisesNightWeightsAndReordersTheTopThree() {
        FishingPoolResolver.SpeciesContext context = bait(RIVER_NIGHT, BaitEffect.WORMS, CharmEffect.LUNA_CHARM);

        assertEquals(List.of(12, 15, 10, 7, 12, 10), weights(context.candidates()));
        assertEquals(List.of(BLENNY, DARTER, MINNOW), topIds(context, 3));
        assertNotEquals(topIds(baseline(RIVER_DAY), 3), topIds(context, 3));
    }

    @Test
    void bananaCharmBoostTouchesOnlyTheYellowGroup() {
        FishingPoolResolver.SpeciesContext plain = baseline(RIVER_DAY);
        FishingPoolResolver.SpeciesContext banana = bait(RIVER_DAY, BaitEffect.WORMS, CharmEffect.BANANA_CHARM);

        assertEquals(8, weightById(plain).get(CYPRINID).intValue());
        Map<String, Integer> boosted = weightById(banana);
        assertEquals(12, boosted.get(CYPRINID).intValue(), "colorful cyprinid is the only COLOR_YELLOW member present");
        assertEquals(ids(plain.candidates()), ids(banana.candidates()));
        for (String id : ids(plain.candidates())) {
            if (!id.equals(CYPRINID)) {
                assertEquals(weightById(plain).get(id), boosted.get(id), "banana must not move " + id);
            }
        }
        assertEquals(List.of(DARTER, BLENNY, CYPRINID), topIds(banana, 3));
    }

    // -------------------------------------------------------------------------
    // Bait pool narrowing and flattening
    // -------------------------------------------------------------------------

    @Test
    void blazedGrubExclusivePoolNarrowsToExoticFish() {
        FishingPoolResolver.SpeciesContext context = bait(RIVER_DAY, BaitEffect.BLAZED_GRUB, null);

        assertEquals(List.of(BLENNY), ids(context.candidates()));
        assertEquals(List.of(12), weights(context.candidates()));
        assertEquals(List.of(BLENNY), topIds(context, 3));
    }

    @Test
    void gummyWormsFlatteningTouchesOnlyCuratedGroupMembers() {
        FishingPoolResolver.SpeciesContext plain = baseline(RIVER_DUSK_RAIN);
        FishingPoolResolver.SpeciesContext gummy = bait(RIVER_DUSK_RAIN, BaitEffect.GUMMY_WORMS, null);

        Map<String, Integer> flattened = weightById(gummy);
        assertEquals(80, flattened.get(DARTER).intValue(), "river darter is in no FISH_GROUPS tag, so flattening skips it");
        assertEquals(11, flattened.get(BLENNY).intValue(), "12 -> 11: exotic blenny is flattened toward DEFAULT_BASE_WEIGHT");
        assertEquals(8, flattened.get(CYPRINID).intValue());
        assertEquals(6, flattened.get(IAPSIS).intValue());
        assertEquals(10, flattened.get(MINNOW).intValue());
        assertEquals(8, flattened.get(POOLFRY).intValue(), "biome multiplier applies after flattening");
        assertEquals(weightById(plain).get(DARTER), flattened.get(DARTER));
    }

    // -------------------------------------------------------------------------
    // Personal equipment (the hook-dependent input, made explicit)
    // -------------------------------------------------------------------------

    @Test
    void diamondBonusAffectsOnlyAcuteIapsisAndMovesTheFingerprint() {
        FishingPoolResolver.SpeciesContext poor = baseline(RIVER_DAY);
        FishingPoolResolver.SpeciesContext rich = resolve(RIVER_DAY, FishingPoolResolver.Inputs.of(BaitEffect.WORMS,
                null, new FishingPoolResolver.PersonalEquipment(7)));

        assertEquals(6, weightById(poor).get(IAPSIS).intValue());
        assertEquals(13, weightById(rich).get(IAPSIS).intValue(), "7 diamonds add 7 weight to diamond-alert iapsis");

        Map<String, Integer> richById = weightById(rich);
        for (String id : ids(poor.candidates())) {
            if (!id.equals(IAPSIS)) {
                assertEquals(weightById(poor).get(id), richById.get(id), "diamonds must not move " + id);
            }
        }
        assertEquals(ids(poor.candidates()), ids(rich.candidates()), "species membership is unaffected");
        assertNotEquals(poor.fingerprint(), rich.fingerprint());
        assertEquals(List.of(DARTER, IAPSIS, BLENNY), topIds(rich, 3), "weight 13 outranks the 12-weight blenny");
    }

    @Test
    void inputsRequireExplicitPersonalEquipment() {
        // Personal equipment is never optional: the caller must say NEUTRAL explicitly. A null has
        // to fail at construction time, not later when the weight math dereferences it.
        assertThrows(NullPointerException.class,
                () -> FishingPoolResolver.Inputs.of(BaitEffect.WORMS, null, null));
    }

    @Test
    void rankingKeepsPoolOrderForTiesAndIsStableAcrossRepeatedResolves() {
        Holder<Item> first = ITEMS.getHolderOrThrow(FishingPoolFixture.itemKey(FishingPoolFixture.DIAMOND_ALERT_IAPSIS));
        Holder<Item> second = ITEMS.getHolderOrThrow(FishingPoolFixture.itemKey(FishingPoolFixture.COLORFUL_CYPRINID));
        Holder<Item> third = ITEMS.getHolderOrThrow(FishingPoolFixture.itemKey(FishingPoolFixture.EXOTIC_BLENNY));
        FishingPoolResolver.SpeciesContext synthetic = new FishingPoolResolver.SpeciesContext(RIVER_DAY,
                List.of(new FishingPoolResolver.WeightedFish(first, 5),
                        new FishingPoolResolver.WeightedFish(second, 9),
                        new FishingPoolResolver.WeightedFish(third, 5)),
                0L);

        assertEquals(List.of(CYPRINID, IAPSIS, BLENNY), ids(FishingPoolResolver.topWeighted(synthetic, 3)),
                "descending by weight; equal weights keep pool order");
        assertEquals(List.of(CYPRINID), ids(FishingPoolResolver.topWeighted(synthetic, 1)));
        assertEquals(List.of(), FishingPoolResolver.topWeighted(synthetic, 0));

        assertEquals(baseline(RIVER_DAY), baseline(RIVER_DAY), "resolving is deterministic and pure");
    }

    // -------------------------------------------------------------------------
    // The real roll: seeded draws reproduce the pre-refactor sequence
    // -------------------------------------------------------------------------

    /**
     * One 12-draw replay exactly as the characterization probe recorded it before the extraction
     * ({@code build/phase3-characterization-before.txt}): environment, bait, charm, quality bias and
     * seed, plus the probe's verbatim {@code draws=} line. Each reward consumes: species draw
     * ({@code nextInt(totalWeight)}), then the quality roll, then the size roll, from one
     * {@code RandomSource}. Any change to that order — or to the number of draws per reward, or to
     * the pool weights feeding the species draw — shifts the sequence, so these pins are what prove
     * the extraction left the real fishing roll's RNG consumption untouched.
     */
    record DrawReplay(String name, FishingPoolResolver.EnvironmentContext environment, BaitEffect baitEffect,
                      CharmEffect charmEffect, float qualityBias, long seed, String expectedDraws) {
    }

    static final List<DrawReplay> DRAW_REPLAYS = List.of(
            new DrawReplay("worms_river_day", RIVER_DAY, BaitEffect.WORMS, null, 0.0f, 42L,
                    "fishtastic:profileless_minnow:COMMON:66.541;fishtastic:river_darter:UNCOMMON:36.005;"
                            + "fishtastic:diamond_alert_iapsis:UNCOMMON:67.726;fishtastic:river_darter:UNCOMMON:50.860;"
                            + "fishtastic:exotic_blenny:COMMON:43.987;fishtastic:exotic_blenny:UNCOMMON:81.027;"
                            + "fishtastic:river_darter:UNCOMMON:62.168;fishtastic:profileless_minnow:UNCOMMON:32.949;"
                            + "fishtastic:river_darter:UNCOMMON:63.613;fishtastic:colorful_cyprinid:COMMON:72.506;"
                            + "fishtastic:colorful_cyprinid:COMMON:42.632;fishtastic:exotic_blenny:COMMON:66.480"),
            new DrawReplay("worms_river_day_banana", RIVER_DAY, BaitEffect.WORMS, CharmEffect.BANANA_CHARM, 0.0f, 42L,
                    "fishtastic:exotic_blenny:COMMON:66.541;fishtastic:river_darter:UNCOMMON:36.005;"
                            + "fishtastic:colorful_cyprinid:UNCOMMON:67.726;fishtastic:river_darter:UNCOMMON:50.860;"
                            + "fishtastic:exotic_blenny:COMMON:43.987;fishtastic:profileless_minnow:UNCOMMON:81.027;"
                            + "fishtastic:river_darter:UNCOMMON:62.168;fishtastic:colorful_cyprinid:UNCOMMON:32.949;"
                            + "fishtastic:river_darter:UNCOMMON:63.613;fishtastic:poolfry:COMMON:72.506;"
                            + "fishtastic:exotic_blenny:COMMON:42.632;fishtastic:colorful_cyprinid:COMMON:66.480"),
            new DrawReplay("worms_river_dusk_rain", RIVER_DUSK_RAIN, BaitEffect.WORMS, null, 0.0f, 42L,
                    "fishtastic:river_darter:COMMON:66.541;fishtastic:river_darter:UNCOMMON:36.005;"
                            + "fishtastic:river_darter:UNCOMMON:67.726;fishtastic:poolfry:UNCOMMON:50.860;"
                            + "fishtastic:river_darter:COMMON:43.987;fishtastic:river_darter:UNCOMMON:81.027;"
                            + "fishtastic:river_darter:UNCOMMON:62.168;fishtastic:river_darter:UNCOMMON:32.949;"
                            + "fishtastic:river_darter:UNCOMMON:63.613;fishtastic:exotic_blenny:COMMON:72.506;"
                            + "fishtastic:exotic_blenny:COMMON:42.632;fishtastic:colorful_cyprinid:COMMON:66.480"),
            new DrawReplay("worms_river_night_luna", RIVER_NIGHT, BaitEffect.WORMS, CharmEffect.LUNA_CHARM, 0.0f, 7L,
                    "fishtastic:profileless_minnow:RARE:81.632;fishtastic:exotic_blenny:UNCOMMON:42.403;"
                            + "fishtastic:profileless_minnow:RARE:54.313;fishtastic:river_darter:EPIC:51.667;"
                            + "fishtastic:river_darter:COMMON:59.096;fishtastic:colorful_cyprinid:UNCOMMON:58.305;"
                            + "fishtastic:poolfry:COMMON:47.245;fishtastic:poolfry:UNCOMMON:27.459;"
                            + "fishtastic:diamond_alert_iapsis:COMMON:34.313;fishtastic:colorful_cyprinid:COMMON:39.526;"
                            + "fishtastic:exotic_blenny:UNCOMMON:10.662;fishtastic:colorful_cyprinid:UNCOMMON:72.041"),
            new DrawReplay("worms_ocean_night_fullmoon", OCEAN_NIGHT, BaitEffect.WORMS, null, 0.0f, 42L,
                    "fishtastic:ocean_glider:COMMON:66.541;fishtastic:ocean_glider:UNCOMMON:36.005;"
                            + "fishtastic:ocean_glider:UNCOMMON:67.726;fishtastic:ocean_glider:UNCOMMON:50.860;"
                            + "fishtastic:ocean_glider:COMMON:43.987;fishtastic:ocean_glider:UNCOMMON:81.027;"
                            + "fishtastic:ocean_glider:UNCOMMON:62.168;fishtastic:ocean_glider:UNCOMMON:32.949;"
                            + "fishtastic:ocean_glider:UNCOMMON:63.613;fishtastic:ocean_glider:COMMON:72.506;"
                            + "fishtastic:ocean_glider:COMMON:42.632;fishtastic:ocean_glider:COMMON:66.480"),
            new DrawReplay("worms_river_high_altitude", RIVER_HIGH_ALTITUDE, BaitEffect.WORMS, null, 0.0f, 42L,
                    "fishtastic:profileless_minnow:COMMON:66.541;fishtastic:river_darter:UNCOMMON:36.005;"
                            + "fishtastic:diamond_alert_iapsis:UNCOMMON:67.726;fishtastic:river_darter:UNCOMMON:50.860;"
                            + "fishtastic:exotic_blenny:COMMON:43.987;fishtastic:exotic_blenny:UNCOMMON:81.027;"
                            + "fishtastic:river_darter:UNCOMMON:62.168;fishtastic:profileless_minnow:UNCOMMON:32.949;"
                            + "fishtastic:river_darter:UNCOMMON:63.613;fishtastic:colorful_cyprinid:COMMON:72.506;"
                            + "fishtastic:colorful_cyprinid:COMMON:42.632;fishtastic:exotic_blenny:COMMON:66.480"),
            new DrawReplay("nobait_river_day", RIVER_DAY, BaitEffect.NO_BAIT, null, 0.0f, 42L,
                    "minecraft:cod:COMMON:66.541;minecraft:salmon:UNCOMMON:36.005;"
                            + "minecraft:cod:UNCOMMON:67.726;minecraft:cod:UNCOMMON:50.860;"
                            + "minecraft:salmon:COMMON:43.987;fishtastic:profileless_minnow:UNCOMMON:81.027;"
                            + "fishtastic:exotic_blenny:UNCOMMON:62.168;minecraft:salmon:UNCOMMON:32.949;"
                            + "minecraft:cod:UNCOMMON:63.613;minecraft:cod:COMMON:72.506;"
                            + "minecraft:salmon:COMMON:42.632;minecraft:salmon:COMMON:66.480"),
            new DrawReplay("nobait_river_night_luna", RIVER_NIGHT, BaitEffect.NO_BAIT, CharmEffect.LUNA_CHARM, 0.0f, 42L,
                    "minecraft:tropical_fish:COMMON:66.541;minecraft:salmon:UNCOMMON:36.005;"
                            + "minecraft:salmon:UNCOMMON:67.726;minecraft:salmon:UNCOMMON:50.860;"
                            + "minecraft:salmon:COMMON:43.987;minecraft:salmon:UNCOMMON:81.027;"
                            + "minecraft:cod:UNCOMMON:62.168;minecraft:cod:UNCOMMON:32.949;"
                            + "minecraft:cod:UNCOMMON:63.613;minecraft:cod:COMMON:72.506;"
                            + "minecraft:pufferfish:COMMON:42.632;minecraft:tropical_fish:COMMON:66.480"),
            new DrawReplay("nobait_ocean_night_fullmoon", OCEAN_NIGHT, BaitEffect.NO_BAIT, null, 0.0f, 42L,
                    "minecraft:pufferfish:COMMON:66.541;minecraft:salmon:UNCOMMON:36.005;"
                            + "minecraft:pufferfish:UNCOMMON:67.726;minecraft:tropical_fish:UNCOMMON:50.860;"
                            + "minecraft:cod:COMMON:43.987;minecraft:salmon:UNCOMMON:81.027;"
                            + "minecraft:salmon:UNCOMMON:62.168;minecraft:cod:UNCOMMON:32.949;"
                            + "minecraft:salmon:UNCOMMON:63.613;minecraft:salmon:COMMON:72.506;"
                            + "minecraft:salmon:COMMON:42.632;minecraft:salmon:COMMON:66.480"),
            new DrawReplay("nobait_river_banana", RIVER_DAY, BaitEffect.NO_BAIT, CharmEffect.BANANA_CHARM, 0.0f, 42L,
                    "minecraft:cod:COMMON:66.541;minecraft:salmon:UNCOMMON:36.005;"
                            + "minecraft:cod:UNCOMMON:67.726;minecraft:cod:UNCOMMON:50.860;"
                            + "minecraft:salmon:COMMON:43.987;fishtastic:profileless_minnow:UNCOMMON:81.027;"
                            + "fishtastic:exotic_blenny:UNCOMMON:62.168;minecraft:salmon:UNCOMMON:32.949;"
                            + "minecraft:cod:UNCOMMON:63.613;minecraft:cod:COMMON:72.506;"
                            + "minecraft:salmon:COMMON:42.632;minecraft:salmon:COMMON:66.480"),
            new DrawReplay("gummy_worms_river_dusk_rain", RIVER_DUSK_RAIN, BaitEffect.GUMMY_WORMS, null, 2.0f, 42L,
                    "fishtastic:river_darter:EPIC:88.176;fishtastic:river_darter:RARE:40.141;"
                            + "fishtastic:river_darter:LEGENDARY:96.197;fishtastic:river_darter:UNCOMMON:50.860;"
                            + "fishtastic:river_darter:UNCOMMON:48.386;fishtastic:exotic_blenny:UNCOMMON:81.027;"
                            + "fishtastic:river_darter:COMMON:56.517;fishtastic:river_darter:UNCOMMON:32.949;"
                            + "fishtastic:river_darter:UNCOMMON:63.613;fishtastic:river_darter:COMMON:72.506;"
                            + "fishtastic:river_darter:COMMON:42.632;fishtastic:river_darter:COMMON:66.480"),
            new DrawReplay("blazed_grub_river_day", RIVER_DAY, BaitEffect.BLAZED_GRUB, null, 0.5f, 42L,
                    "fishtastic:exotic_blenny:COMMON:66.541;fishtastic:exotic_blenny:UNCOMMON:36.005;"
                            + "fishtastic:exotic_blenny:RARE:73.305;fishtastic:exotic_blenny:UNCOMMON:50.860;"
                            + "fishtastic:exotic_blenny:COMMON:43.987;fishtastic:exotic_blenny:UNCOMMON:81.027;"
                            + "fishtastic:exotic_blenny:UNCOMMON:62.168;fishtastic:exotic_blenny:COMMON:29.954;"
                            + "fishtastic:exotic_blenny:COMMON:57.830;fishtastic:exotic_blenny:UNCOMMON:79.756;"
                            + "fishtastic:exotic_blenny:COMMON:42.632;fishtastic:exotic_blenny:UNCOMMON:73.128"),
            new DrawReplay("worms_river_day_fourleaf_quality", RIVER_DAY, BaitEffect.WORMS, CharmEffect.FOUR_LEAF_CHARM,
                    2.0f, 42L,
                    "fishtastic:profileless_minnow:EPIC:88.176;fishtastic:river_darter:RARE:40.141;"
                            + "fishtastic:diamond_alert_iapsis:LEGENDARY:96.197;fishtastic:river_darter:UNCOMMON:50.860;"
                            + "fishtastic:exotic_blenny:UNCOMMON:48.386;fishtastic:exotic_blenny:UNCOMMON:81.027;"
                            + "fishtastic:river_darter:COMMON:56.517;fishtastic:profileless_minnow:UNCOMMON:32.949;"
                            + "fishtastic:river_darter:UNCOMMON:63.613;fishtastic:colorful_cyprinid:COMMON:72.506;"
                            + "fishtastic:colorful_cyprinid:COMMON:42.632;fishtastic:exotic_blenny:COMMON:66.480"),
            new DrawReplay("worms_river_day_almanac_charm", RIVER_DAY, BaitEffect.WORMS, CharmEffect.ANGLERS_ALMANAC,
                    0.0f, 42L,
                    "fishtastic:profileless_minnow:COMMON:66.541;fishtastic:river_darter:UNCOMMON:36.005;"
                            + "fishtastic:diamond_alert_iapsis:UNCOMMON:67.726;fishtastic:river_darter:UNCOMMON:50.860;"
                            + "fishtastic:exotic_blenny:COMMON:43.987;fishtastic:exotic_blenny:UNCOMMON:81.027;"
                            + "fishtastic:river_darter:UNCOMMON:62.168;fishtastic:profileless_minnow:UNCOMMON:32.949;"
                            + "fishtastic:river_darter:UNCOMMON:63.613;fishtastic:colorful_cyprinid:COMMON:72.506;"
                            + "fishtastic:colorful_cyprinid:COMMON:42.632;fishtastic:exotic_blenny:COMMON:66.480"),
            new DrawReplay("null_bait_river_day", RIVER_DAY, null, null, 0.0f, 42L,
                    "minecraft:cod:COMMON:66.541;minecraft:cod:UNCOMMON:36.005;"
                            + "minecraft:pufferfish:UNCOMMON:67.726;fishtastic:river_darter:UNCOMMON:50.860;"
                            + "minecraft:salmon:COMMON:43.987;minecraft:salmon:UNCOMMON:81.027;"
                            + "minecraft:cod:UNCOMMON:62.168;minecraft:cod:UNCOMMON:32.949;"
                            + "minecraft:salmon:UNCOMMON:63.613;fishtastic:diamond_alert_iapsis:COMMON:72.506;"
                            + "minecraft:cod:COMMON:42.632;minecraft:salmon:COMMON:66.480"));

    @Test
    void seededRewardDrawsReproduceThePreRefactorSequence() {
        for (DrawReplay replay : DRAW_REPLAYS) {
            FishingPoolResolver.SpeciesContext context = resolve(replay.environment(),
                    FishingPoolResolver.Inputs.of(replay.baitEffect(), replay.charmEffect(),
                            FishingPoolResolver.PersonalEquipment.NEUTRAL));
            List<Holder<Item>> pool = FishingPoolResolver.candidatePool(ITEMS, PROFILES, replay.environment(),
                    replay.baitEffect());
            RandomSource randomSource = RandomSource.create(replay.seed());

            List<String> draws = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                ItemStack stack = FishtasticFishItem.sampleRandomFish(randomSource, context.candidates(), PROFILES,
                        replay.qualityBias());
                int index = FishingPoolFixture.indexOf(pool, stack.getItem());
                draws.add((index >= 0 ? FishingPoolFixture.idOf(pool.get(index)) : "EMPTY")
                        + ":" + FishQualityHelper.getQuality(stack)
                        + ":" + String.format(Locale.ROOT, "%.3f", ItemSizeHelper.getSize(stack)));
            }

            assertEquals(replay.expectedDraws(), String.join(";", draws), replay.name());
        }
    }

    // -------------------------------------------------------------------------
    // Resolution is pure: it consumes no random numbers
    // -------------------------------------------------------------------------

    @Test
    void resolvingAPoolConsumesNoRandomNumbers() {
        RandomSource control = RandomSource.create(20261013L);
        RandomSource subject = RandomSource.create(20261013L);
        for (int i = 0; i < 5; i++) {
            resolve(RIVER_NIGHT, FishingPoolResolver.Inputs.of(BaitEffect.GUMMY_WORMS, CharmEffect.LUNA_CHARM,
                    new FishingPoolResolver.PersonalEquipment(7)));
        }

        List<Integer> controlDraws = new ArrayList<>();
        List<Integer> subjectDraws = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            controlDraws.add(control.nextInt(1000));
            subjectDraws.add(subject.nextInt(1000));
        }
        assertEquals(controlDraws, subjectDraws,
                "resolution must not touch the catch RNG stream — a resolution that consumed draws would desync the subject");
    }

    // -------------------------------------------------------------------------
    // Fingerprint: species-affecting inputs only
    // -------------------------------------------------------------------------

    @Test
    void fingerprintIsStableAndSensitiveToEverySpeciesAffectingInput() {
        long base = baseline(RIVER_DAY).fingerprint();

        assertEquals(base, baseline(RIVER_DAY).fingerprint());
        assertNotEquals(base, baseline(RIVER_DAY_RAIN).fingerprint(), "weather");
        assertNotEquals(base, baseline(RIVER_DAY_FULL_MOON).fingerprint(), "moon phase");
        assertNotEquals(base, baseline(RIVER_NIGHT).fingerprint(), "time of day");
        assertNotEquals(base, baseline(RIVER_HIGH_ALTITUDE).fingerprint(), "zones");
        assertNotEquals(base, bait(RIVER_DAY, BaitEffect.GUMMY_WORMS, null).fingerprint(), "bait");
        assertNotEquals(base, bait(RIVER_DAY, BaitEffect.WORMS, CharmEffect.BANANA_CHARM).fingerprint(), "charm");
        assertNotEquals(base, resolve(RIVER_DAY, FishingPoolResolver.Inputs.of(BaitEffect.WORMS, null,
                new FishingPoolResolver.PersonalEquipment(3))).fingerprint(), "personal equipment");

        FishingPoolResolver.EnvironmentContext otherBiome = new FishingPoolResolver.EnvironmentContext(
                Holder.direct(new Biome.BiomeBuilder()
                        .temperature(0.9f)
                        .downfall(0.9f)
                        .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x112233).waterFogColor(0x050533)
                                .fogColor(0xC0D8FF).skyColor(0x78A7FF).build())
                        .mobSpawnSettings(MobSpawnSettings.EMPTY)
                        .generationSettings(BiomeGenerationSettings.EMPTY)
                        .build()),
                RIVER_DAY.timeOfDay(), RIVER_DAY.weather(), RIVER_DAY.moonPhase(), RIVER_DAY.zones());
        assertNotEquals(base, baseline(otherBiome).fingerprint(), "biome");
    }

    @Test
    void fingerprintIgnoresInputsThatCannotChangeSpecies() {
        // Two baits identical in every species-affecting field, different in everything else.
        BaitEffect plain = new BaitEffect(0f, 0.1f, 0.05f, 0, 1f, 0f, Optional.empty(), List.of());
        BaitEffect tuned = new BaitEffect(0.9f, 0.9f, 0.4f, 3, 1f, 5f, Optional.empty(), List.of(), 1.0f, true);
        assertEquals(baseline(RIVER_DAY).fingerprint(), bait(RIVER_DAY, plain, null).fingerprint());
        assertEquals(bait(RIVER_DAY, plain, null).fingerprint(), bait(RIVER_DAY, tuned, null).fingerprint(),
                "luck/treasure/trash/target-count/quality-bias/small-bobber are not species-affecting");

        // Two charms identical in night bonus and affinities, different in everything else.
        CharmEffect quiet = new CharmEffect(1.0f, false, 0f, 1f, false, 1.0f, List.of(), false, false, 0f, 0f);
        CharmEffect loud = new CharmEffect(2.5f, true, 0.4f, 3f, false, 1.0f, List.of(), true, true, 0.5f, 30f);
        assertEquals(bait(RIVER_DAY, BaitEffect.WORMS, quiet).fingerprint(),
                bait(RIVER_DAY, BaitEffect.WORMS, loud).fingerprint(),
                "input force/outline/trash/auto-pile/bait-save/sunset are not species-affecting");

        // forceNightFishing is not itself hashed: it only ever acts through the environment's
        // timeOfDay, which is already an input. Same environment, same fingerprint, either way.
        CharmEffect forceNight = new CharmEffect(1.0f, false, 0f, 1f, true, 1.0f, List.of(), false, false, 0f, 0f);
        assertEquals(bait(RIVER_DAY, BaitEffect.WORMS, quiet).fingerprint(),
                bait(RIVER_DAY, BaitEffect.WORMS, forceNight).fingerprint());
    }

    @Test
    void fingerprintHashesTheResolvedPoolItself() {
        // Same environment, same inputs, same candidate ids — only exotic blenny's profile base
        // weight moved (12 -> 13) in a one-off registry. If the |pool= section were dropped from
        // the hashed string, this would be the only test to fail.
        MappedRegistry<FishProfile> variant =
                FishingPoolFixture.profilesWithBaseWeight(FishingPoolFixture.EXOTIC_BLENNY, 13);
        FishingPoolResolver.SpeciesContext changed = FishingPoolResolver.resolve(ITEMS, variant, RIVER_DAY,
                FishingPoolResolver.Inputs.standardWormsBaseline());

        assertEquals(13, weightById(changed).get(BLENNY).intValue(), "the variant registry must really be in effect");
        assertEquals(ids(baseline(RIVER_DAY).candidates()), ids(changed.candidates()),
                "pool membership is unchanged, so only the weight part of |pool= can differ");
        assertNotEquals(baseline(RIVER_DAY).fingerprint(), changed.fingerprint(),
                "pool weights are species-affecting and must be hashed by |pool=");
    }

    @Test
    void fingerprintExactValueIsPinned() {
        // Exact FNV-1a 64 of the canonical species-affecting string for the RIVER_DAY baseline in
        // the fixture world (biome|time|weather|moon|zones|bait|charm|diamonds|pool). This constant
        // must change ONLY when a species-affecting input is added to, removed from or reordered in
        // that string — or when the fixture world itself changes (species, weights, biome). Its job
        // is to make an accidentally dropped hashed field fail loudly: the relational tests above
        // would all stay green if, say, the whole |pool= loop silently disappeared. The fixture
        // biome is registry-keyed precisely so this value is stable across JVM runs.
        assertEquals(0xFCF605A39DF5AAB9L, baseline(RIVER_DAY).fingerprint(),
                "if this pin moves, a hashed species-affecting input was lost or reordered");
    }

    @Test
    void candidatePoolIsImmutable() {
        List<Holder<Item>> pool = FishingPoolResolver.candidatePool(ITEMS, PROFILES, RIVER_DAY, BaitEffect.WORMS);
        assertEquals(List.of(DARTER, BLENNY, CYPRINID, IAPSIS, MINNOW, POOLFRY),
                pool.stream().map(FishingPoolFixture::idOf).toList());
        assertThrows(UnsupportedOperationException.class, () -> pool.add(null));
    }
}
