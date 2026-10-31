package grill24.fishtastic.server;

import grill24.fishtastic.client.ambient.VisualSampler;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.AmbientFluid;
import grill24.fishtastic.ecology.AmbientRoster;
import grill24.fishtastic.ecology.NaturalPool;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Species for the seeded ambient fish over the <em>shipped</em> fish profiles (loaded headlessly by
 * {@link AmbientPresenceReportTest#load}): vanilla fish are never ambient, the Nether's pool is the
 * nether roster (swimming in lava), and two independent clients derive the same IDs and species.
 */
class AmbientRosterSpeciesTest {

    private static final AmbientRoster.Params P = AmbientRoster.DEFAULT;

    /** One simulated client: its own sampler cache, deriving fish from (seed, chunk, volume, time) alone. */
    private static final class Client {
        final AmbientPresenceReportTest.Loaded data;
        final Holder<Biome> biome = FishingPoolFixture.riverBiome();
        final Map<FishingPoolResolver.EnvironmentContext, VisualSampler> samplers = new HashMap<>();
        final Set<FishProfile.Zone> forcedZones;

        Client(AmbientPresenceReportTest.Loaded data, Set<FishProfile.Zone> forcedZones) {
            this.data = data;
            this.forcedZones = forcedZones;
        }

        record Fish(long id, String species) {
        }

        List<Fish> derive(long seed, int cx, int cz, int usableCells, long time, long clockOffset,
                          FishProfile.WeatherCondition weather) {
            List<Fish> out = new ArrayList<>();
            int slots = AmbientRoster.slotCount(usableCells, P);
            for (AmbientRoster.Lifespan l : AmbientRoster.present(seed, cx, cz, slots, time, P)) {
                var env = NaturalPool.ambientEnvironment(biome, l.arrivalTick() + clockOffset, weather, 64, 63);
                if (forcedZones != null) {
                    env = new FishingPoolResolver.EnvironmentContext(biome, env.timeOfDay(), env.weather(),
                            env.moonPhase(), forcedZones);
                }
                VisualSampler sampler = samplers.computeIfAbsent(env, e -> VisualSampler.build(
                        NaturalPool.ambientCandidates(data.profiles(), NaturalPool.resolve(data.items(), data.profiles(), e)),
                        id -> true));
                out.add(new Fish(l.fishId(), sampler.sampleAt(AmbientRoster.speciesUnit(l.fishId()))));
            }
            return out;
        }
    }

    @Test
    void vanillaFishAreNeverAmbientInAnyLocationPoolOrCondition() throws IOException {
        var data = AmbientPresenceReportTest.load();
        Holder<Biome> biome = FishingPoolFixture.riverBiome();
        int pools = 0;
        for (Set<FishProfile.Zone> zones : EncyclopediaTiers.LOCATION_POOLS) {
            for (var time : FishProfile.TimeOfDay.values()) {
                for (var weather : FishProfile.WeatherCondition.values()) {
                    for (var moon : FishMoonPhase.values()) {
                        var env = new FishingPoolResolver.EnvironmentContext(biome, time, weather, moon, Set.copyOf(zones));
                        var pool = NaturalPool.resolve(data.items(), data.profiles(), env);
                        assertTrue(pool.candidates().stream().anyMatch(f -> f.item().unwrapKey().orElseThrow()
                                .location().getNamespace().equals("minecraft")), "the catch pool still holds vanilla fish");
                        var candidates = NaturalPool.ambientCandidates(data.profiles(), pool);
                        assertFalse(candidates.isEmpty(), zones + " has an ambient roster");
                        for (var c : candidates) {
                            assertEquals("fishtastic", c.species().getNamespace(), "vanilla leaked: " + c.species());
                        }
                        VisualSampler sampler = VisualSampler.build(candidates, id -> true);
                        for (int i = 0; i < 200; i++) {
                            String species = sampler.sampleAt((i + 0.5) / 200.0);
                            assertNotNull(species);
                            assertTrue(species.startsWith("fishtastic:"), species);
                        }
                        pools++;
                    }
                }
            }
        }
        assertTrue(pools > 800, "swept " + pools + " pools");
    }

    @Test
    void theNetherRosterIsTheShippedNetherPoolAndSwimsInLava() throws IOException {
        var data = AmbientPresenceReportTest.load();
        assertEquals(AmbientFluid.LAVA, AmbientFluid.forDimension("minecraft:the_nether"));
        Set<FishProfile.Zone> nether = Set.of(FishProfile.Zone.NETHER);
        Client client = new Client(data, nether);
        Set<String> seen = new java.util.TreeSet<>();
        for (long time = 0; time < 240_000; time += 4800) {
            for (var fish : client.derive(7L, 2, 2, 800, time, 0, FishProfile.WeatherCondition.CLEAR)) {
                assertNotNull(fish.species());
                seen.add(fish.species());
            }
        }
        assertFalse(seen.isEmpty());
        for (String species : seen) {
            var profile = data.byName().get(species.substring("fishtastic:".length()));
            assertNotNull(profile, species);
            assertTrue(profile.zones().contains(FishProfile.Zone.NETHER), species + " is not a nether species");
        }
        System.out.println("nether ambient species seen: " + seen);
    }

    @Test
    void ambientEnvironmentFollowsTheArrivalClock() throws IOException {
        Holder<Biome> biome = FishingPoolFixture.riverBiome();
        var weather = FishProfile.WeatherCondition.RAIN;
        var dawn = NaturalPool.ambientEnvironment(biome, 24_000L * 3 + 23_000L, weather, 64, 63);
        assertEquals(FishProfile.TimeOfDay.DAWN, dawn.timeOfDay());
        assertEquals(FishMoonPhase.fromIndex(3), dawn.moonPhase());
        assertEquals(weather, dawn.weather());
        var night = NaturalPool.ambientEnvironment(biome, 24_000L * 9 + 16_000L, weather, 64, 63);
        assertEquals(FishProfile.TimeOfDay.NIGHT, night.timeOfDay());
        assertEquals(FishMoonPhase.fromIndex(1), night.moonPhase());
        var day = NaturalPool.ambientEnvironment(biome, 5_000L, weather, 64, 63);
        assertEquals(FishProfile.TimeOfDay.DAY, day.timeOfDay());
    }

    /**
     * Plan scene "two client copies list the same IDs and species for the same water and time",
     * headless: two independent {@link Client}s (separate caches, built in opposite orders) over the same
     * seed, chunks, volumes, clock and weather. A live two-client scene is still owed.
     */
    @Test
    void twoIndependentClientsListTheSameIdsAndSpecies() throws IOException {
        var data = AmbientPresenceReportTest.load();
        Client a = new Client(data, null), b = new Client(data, null);
        int[][] chunks = {{0, 0, 1024}, {1, 0, 300}, {-1, 4, 80}, {7, -3, 640}, {-9, -9, 20}};
        int compared = 0;
        Set<String> species = new java.util.TreeSet<>();
        for (long time = 0; time < 96_000; time += 733) {
            for (int[] chunk : chunks) {
                var fromA = a.derive(0xFEEDL, chunk[0], chunk[1], chunk[2], time, 5_000, FishProfile.WeatherCondition.CLEAR);
                var fromB = b.derive(0xFEEDL, chunk[0], chunk[1], chunk[2], time, 5_000, FishProfile.WeatherCondition.CLEAR);
                assertEquals(fromA, fromB);
                for (var fish : fromA) species.add(fish.species());
                compared += fromA.size();
            }
        }
        assertTrue(compared > 1000, "compared " + compared + " fish");
        assertTrue(species.size() > 5, "a varied roster, got " + species);
        // A client that arrives later (derives only at a later time) agrees on the fish still present.
        Client late = new Client(data, null);
        var early = a.derive(0xFEEDL, 0, 0, 1024, 50_000, 5_000, FishProfile.WeatherCondition.CLEAR);
        var afterwards = late.derive(0xFEEDL, 0, 0, 1024, 50_000, 5_000, FishProfile.WeatherCondition.CLEAR);
        assertEquals(early, afterwards);
        // Different seeds give different worlds.
        assertFalse(early.equals(a.derive(0xBEEFL, 0, 0, 1024, 50_000, 5_000, FishProfile.WeatherCondition.CLEAR)));
    }
}
