package grill24.fishtastic.client.util;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticObstacles;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.ShippedStructures;
import grill24.fishtastic.fishtank.SpanStructures;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solid cosmetics must not cost shelters their use (docs/fish-shelters.md §12.3): with every
 * shipped shelter's own parts solid round it, fish still get in as often per attempt, through the
 * same mouths, and still swim through the ones that pass through.
 *
 * <p>Fewer attempts are expected and allowed: a structure whose bulk is now solid (the whale's
 * ribcage and spine) keeps fish further from its shelter, so they set out for it less often.
 * What may not drop is what happens once they do — that would mean an obstacle in the way.
 */
class ObstacleShelterAccessTest {

    private static final long[] SEEDS = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int TICKS = 6_000;
    private static final int FISH = 12;

    /** Shelters whose control run passed through — see {@link #passThroughStaysMeasurable}. */
    private static final Set<String> measuredPassThrough = new TreeSet<>();

    /** Approaches, entries, the mouths entered by, and the visits that left by another mouth. */
    private record Visits(int approaches, int entries, Set<Integer> mouths, int throughs) {}

    private static Visits run(CosmeticStructure structure, String domainName, boolean obstacles) {
        int approaches = 0, entries = 0, throughs = 0;
        Set<Integer> mouths = new TreeSet<>();
        for (long seed : SEEDS) {
            boolean[][][] occupancy = Scenarios.occupancy(domainName);
            int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
            boolean span = structure.span().isPresent();
            float ox = span ? sx / 2f : 0.5f, oy = sy / 2f, oz = span ? sz / 2f : sz / 2f;
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
            domain.rebuildShelters(List.of(TankShelters.toEngine(
                    TankShelters.inBlockFrame(structure, anchorX, anchorZ, Rotation.NONE).orElseThrow(), ox, oy, oz, 0f)));
            if (obstacles) {
                domain.rebuildObstacles(TankObstacles.toEngine(TankObstacles.inBlockFrame(
                        CosmeticObstacles.derive(structure, ShippedStructures.softTag()), structure.scale(),
                        anchorX, anchorZ, Rotation.NONE), ox, oy, oz, 0f));
            }
            FlockEngine engine = new FlockEngine(Tunables.GROUP);
            FishSpec[] specs = Scenarios.visitors(FISH, seed);
            engine.rebuild(specs, seed, 0f, 20f, domain);
            int[] state = new int[FISH], enteredBy = new int[FISH];
            for (int tick = 0; tick < TICKS; tick++) {
                engine.step();
                for (int i = 0; i < FISH; i++) {
                    int now = engine.shelterState(i);
                    if (state[i] == FlockEngine.SHELTER_ROAMING && now == FlockEngine.SHELTER_APPROACH) approaches++;
                    if (state[i] == FlockEngine.SHELTER_APPROACH && now == FlockEngine.SHELTER_ENTER) {
                        entries++;
                        enteredBy[i] = engine.shelterMouth(i);
                        mouths.add(enteredBy[i]);
                    }
                    if (state[i] != now && now == FlockEngine.SHELTER_ROAMING && state[i] == FlockEngine.SHELTER_EXIT
                            && engine.shelterMouth(i) != enteredBy[i]) {
                        throughs++;
                    }
                    state[i] = now;
                }
            }
        }
        return new Visits(approaches, entries, mouths, throughs);
    }

    @TestFactory
    List<DynamicTest> sheltersStayAsUsableWithSolidCosmetics() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure structure = e.getValue();
            if (structure.shelterShape().isEmpty()) continue;
            String domainName = structure.span()
                    .map(s -> s.x() + "x" + s.y() + "x" + s.z()).orElse("3x1x1");
            tests.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                Visits without = run(structure, domainName, false);
                Visits with = run(structure, domainName, true);
                String report = e.getKey() + ": without " + without + ", with " + with;
                assertTrue(without.entries() > 0, "no visits at all to measure against — " + report);
                float rateWithout = without.entries() / (float) without.approaches();
                float rateWith = with.entries() / (float) Math.max(1, with.approaches());
                assertTrue(rateWith >= 0.8f * rateWithout, "entries per approach fell — " + report);
                assertTrue(with.mouths().containsAll(without.mouths()), "a mouth fell out of use — " + report);
                // The control has to show the pass-through before the with-obstacles run can be held
                // to it. Demanding it of every multi-mouth shelter fails on a baseline fact, not a
                // regression: the Hollow Log's fish only ever use one mouth in this domain. The set
                // as a whole must measure it — see passThroughStaysMeasurable.
                if (without.throughs() > 0) {
                    assertTrue(with.throughs() > 0, "fish stopped passing through — " + report);
                    measuredPassThrough.add(e.getKey());
                }
            }));
        }
        return tests;
    }

    /** At least one shelter has to exercise the pass-through, or the check above guards nothing. */
    @AfterAll
    static void passThroughStaysMeasurable() {
        assertTrue(!measuredPassThrough.isEmpty(),
                "no shipped shelter's control passed through, so nothing measured the property");
    }
}
