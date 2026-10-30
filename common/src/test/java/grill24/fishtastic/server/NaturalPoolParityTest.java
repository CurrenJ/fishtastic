package grill24.fishtastic.server;

import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.NaturalPool;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The extracted natural pool must equal the pre-refactor path - the inputs
 * {@code FishSchoolManager#tryCreateSchool} used to build by hand
 * ({@code Inputs.of(NO_BAIT, null, NEUTRAL)} through {@link FishingPoolResolver#resolve}) - for every
 * zone, time of day, weather and moon phase. Biome and sea level reach the pool only through
 * {@link FishProfile.Zone#resolve}, which {@link #environmentIsAssembledLikeBefore} pins at several
 * heights; resolving zones from real biome tags (nether, ocean, caves) needs a running game.
 */
class NaturalPoolParityTest {

    static final MappedRegistry<Item> ITEMS = FishingPoolFixture.items();
    static final MappedRegistry<FishProfile> PROFILES = FishingPoolFixture.profiles();

    static {
        FishingPoolFixture.ensureDataComponents();
    }

    private static final FishingPoolResolver.Inputs PRE_REFACTOR = FishingPoolResolver.Inputs.of(
            BaitEffect.NO_BAIT, null, FishingPoolResolver.PersonalEquipment.NEUTRAL);

    @Test
    void matchesThePreRefactorPathForEveryZoneTimeWeatherAndMoon() {
        Holder<Biome> biome = FishingPoolFixture.riverBiome();
        int cases = 0;
        for (FishProfile.Zone zone : FishProfile.Zone.values()) {
            Set<FishProfile.Zone> zones = EnumSet.of(zone);
            for (FishProfile.TimeOfDay time : FishProfile.TimeOfDay.values()) {
                for (FishProfile.WeatherCondition weather : FishProfile.WeatherCondition.values()) {
                    for (FishMoonPhase moon : FishMoonPhase.values()) {
                        var env = new FishingPoolResolver.EnvironmentContext(biome, time, weather, moon, zones);
                        var expected = FishingPoolResolver.resolve(ITEMS, PROFILES, env, PRE_REFACTOR);
                        var actual = NaturalPool.resolve(ITEMS, PROFILES, env);
                        assertEquals(expected, actual, zone + "/" + time + "/" + weather + "/" + moon);
                        assertEquals(expected.fingerprint(), actual.fingerprint());
                        cases++;
                    }
                }
            }
        }
        assertEquals(FishProfile.Zone.values().length * FishProfile.TimeOfDay.values().length
                * FishProfile.WeatherCondition.values().length * FishMoonPhase.values().length, cases);
    }

    @Test
    void naturalPoolKeepsVanillaFishAndIgnoresTackle() {
        var env = new FishingPoolResolver.EnvironmentContext(FishingPoolFixture.riverBiome(),
                FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, FishMoonPhase.NEW_MOON,
                FishingPoolFixture.RIVER_ZONES);
        var pool = NaturalPool.resolve(ITEMS, PROFILES, env);
        List<String> ids = pool.candidates().stream().map(c -> FishingPoolFixture.idOf(c.item())).toList();
        assertTrue(ids.contains("minecraft:cod"), ids.toString());
        assertTrue(ids.contains("fishtastic:river_darter"), ids.toString());
    }

    @Test
    void environmentIsAssembledLikeBefore() {
        Holder<Biome> biome = FishingPoolFixture.riverBiome();
        int seaLevel = 63;
        for (int y : new int[]{-60, 40, 63, 64, 75, 100, 200, 300}) {
            for (FishProfile.TimeOfDay time : FishProfile.TimeOfDay.values()) {
                var built = NaturalPool.environment(biome, time, FishProfile.WeatherCondition.RAIN,
                        FishMoonPhase.FULL_MOON, y, seaLevel);
                assertEquals(new FishingPoolResolver.EnvironmentContext(biome, time, FishProfile.WeatherCondition.RAIN,
                        FishMoonPhase.FULL_MOON, FishProfile.Zone.resolve(biome, y, seaLevel)), built);
            }
        }
    }
}
