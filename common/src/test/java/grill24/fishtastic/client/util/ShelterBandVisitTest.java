package grill24.fishtastic.client.util;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticObstacles;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.ShippedStructures;
import grill24.fishtastic.fishtank.SpanStructures;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shelters built for medium and large fish (docs/fish-shelters.md §12.9) are used by them in a
 * running tank, with the structure's parts solid round them: a shoal of fish the size the shelter
 * was built for, in the group shape it needs, gets in. {@link ShelterSizeBandsTest} holds the fit
 * on paper; this holds that the fish get there (staging room, corridors, the obstacles).
 *
 * <p>Floor structures stand in the middle tank of the domain, unturned: their big-fish mouths face
 * along x, so a 3x1x1 has the room; the Moon Gate's passage runs along z, so it gets a 1x1x3.
 */
class ShelterBandVisitTest {

    private static final long[] SEEDS = {1, 2, 3};
    private static final int TICKS = 6_000;
    private static final int FISH = 8;

    /** A run: this structure, in this domain, with a shoal this long, must enter one of these shelters (each group). */
    private record Case(String structure, String domain, float length, ShelterUse use, int[][] mustEnter) {
        String name() {
            return structure + " " + domain + " L=" + length + " " + use;
        }
    }

    private static int[][] each(int... shelters) {
        int[][] out = new int[shelters.length][];
        for (int i = 0; i < shelters.length; i++) out[i] = new int[]{shelters[i]};
        return out;
    }

    private static List<Case> cases() {
        return List.of(
                new Case("amphora", "3x1x1", 0.34f, ShelterUse.VISITOR, each(0)),
                new Case("drowned_bell", "3x1x1", 0.34f, ShelterUse.VISITOR, each(0)),
                new Case("mangrove_knees", "3x1x1", 0.40f, ShelterUse.VISITOR, each(0)),
                new Case("basalt_grotto", "3x1x1", 0.50f, ShelterUse.VISITOR, each(0)),
                new Case("basalt_grotto", "3x1x1", 0.50f, ShelterUse.LURKER, each(0)),
                new Case("leviathans_seat", "3x1x1", 0.45f, ShelterUse.NONE, each(0)),
                new Case("moon_gate", "1x1x3", 0.60f, ShelterUse.NONE, each(0)),
                new Case("sea_arch", "2x2x2", 0.60f, ShelterUse.NONE, each(0)),
                new Case("sunken_ziggurat", "4x2x1", 0.50f, ShelterUse.VISITOR, each(0)),
                new Case("sunken_ziggurat", "4x2x1", 0.15f, ShelterUse.NONE, each(1)),
                new Case("capsized_galleon", "3x2x1", 0.50f, ShelterUse.LURKER, each(0)),
                new Case("whale_fall", "4x2x2", 0.60f, ShelterUse.NONE, each(1)),
                new Case("drowned_cathedral", "4x2x2", 0.60f, ShelterUse.NONE, each(0)),
                new Case("drowned_cathedral", "4x2x2", 0.30f, ShelterUse.NONE, each(1)),
                new Case("drowned_cathedral", "4x2x2", 0.15f, ShelterUse.VISITOR, new int[][]{{2, 3, 4}}),
                new Case("coral_warren", "3x2x1", 0.20f, ShelterUse.NONE, each(0, 2)),
                new Case("coral_warren", "3x2x1", 0.35f, ShelterUse.NONE, each(1)));
    }

    /** Entries into each of the structure's shelters, summed over the seeds. */
    private static int[] entries(Case c) {
        CosmeticStructure structure = ShippedStructures.all().get(c.structure());
        int[] counts = new int[structure.shelters().size()];
        for (long seed : SEEDS) {
            boolean[][][] occupancy = Scenarios.occupancy(c.domain());
            int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
            boolean span = structure.span().isPresent();
            // The engine's origin in the structure's tank's block frame: a span's min tank, else the middle one.
            float ox = span ? sx / 2f : sx / 2f - sx / 2, oy = sy / 2f, oz = span ? sz / 2f : sz / 2f - sz / 2;
            float anchorX, anchorZ;
            if (span) {
                float[] origin = SpanStructures.buildOrigin(structure, Rotation.NONE);
                anchorX = origin[0];
                anchorZ = origin[1];
            } else {
                CosmeticGridCell anchor = new CosmeticGridCell(1, 1);
                anchorX = (float) anchor.localX();
                anchorZ = (float) anchor.localZ();
            }
            VoxelDomain domain = new VoxelDomain(occupancy);
            domain.rebuildShelters(TankShelters.inBlockFrame(structure, anchorX, anchorZ, Rotation.NONE).stream()
                    .map(shelter -> TankShelters.toEngine(shelter, ox, oy, oz, 0f)).toList());
            domain.rebuildObstacles(TankObstacles.toEngine(TankObstacles.inBlockFrame(
                    CosmeticObstacles.derive(structure, ShippedStructures.softTag()), structure.scale(),
                    anchorX, anchorZ, Rotation.NONE), ox, oy, oz, 0f));
            Random r = new Random(seed * 31 + 7);
            FishSpec[] specs = new FishSpec[FISH];
            for (int i = 0; i < FISH; i++) {
                // A lurker cast is one lurker among visitors, as in a real tank.
                ShelterUse use = c.use() == ShelterUse.LURKER && i > 0 ? ShelterUse.VISITOR : c.use();
                specs[i] = new FishSpec(c.length() * (0.96f + 0.08f * r.nextFloat()), Locomotion.FREE_SWIM,
                        r.nextBoolean(), 1, use);
            }
            FlockEngine engine = new FlockEngine(Tunables.GROUP);
            engine.rebuild(specs, seed, 0f, 20f, domain);
            int[] state = new int[FISH];
            for (int tick = 0; tick < TICKS; tick++) {
                engine.step();
                for (int i = 0; i < FISH; i++) {
                    int now = engine.shelterState(i);
                    if (now == FlockEngine.SHELTER_ENTER && state[i] != FlockEngine.SHELTER_ENTER) {
                        int s = engine.shelterUsing(i);
                        if (s >= 0) counts[s]++;
                    }
                    state[i] = now;
                }
            }
        }
        return counts;
    }

    @TestFactory
    List<DynamicTest> eachShelterIsUsedByTheFishItWasBuiltFor() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Case c : cases()) {
            tests.add(DynamicTest.dynamicTest(c.name(), () -> {
                int[] counts = entries(c);
                for (int[] group : c.mustEnter()) {
                    int sum = 0;
                    for (int s : group) sum += counts[s];
                    assertTrue(sum > 0, c.name() + ": no entries into shelter(s) " + Arrays.toString(group)
                            + "; entries per shelter " + Arrays.toString(counts));
                }
                System.out.println("[bands] " + c.name() + " entries " + Arrays.toString(counts));
            }));
        }
        return tests;
    }
}
