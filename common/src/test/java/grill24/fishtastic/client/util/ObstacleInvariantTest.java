package grill24.fishtastic.client.util;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Metrics;
import grill24.fishsim.harness.Scenarios;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticObstacles;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.ShippedStructures;
import grill24.fishtastic.fishtank.SpanStructures;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The obstacle invariant over every shipped cosmetic (docs/fish-shelters.md §12.3): a swimmer not
 * using a shelter is never inside a solid obstacle box, the hard backstop never engages, and the
 * dynamics and stuck bounds the shelter matrix holds ({@code ShelterHullTest},
 * {@code ShelterVisitTest}) hold here too. Real structures through the real derivation and frame
 * mapping, not synthetic boxes: the shipped set is where the thin posts, overhangs and spans are.
 *
 * <p>Each floor structure stands in the middle tank of a 3×1×1 group at 6 and 12 fish, of a 3×2×2
 * at 6, and in a promoted lone tank at 6; a spanning one fills its own box. Rotations alternate with the seed.
 */
class ObstacleInvariantTest {

    private static final float SPEED_TOLERANCE = 1.001f;
    private static final float ACCEL_TOLERANCE = 1.06f;
    private static final float MAX_JERK = 12f;
    private static final float MIN_WINDOW_DISPLACEMENT = 0.05f;
    private static final int WINDOW_TICKS = 600;
    private static final int TICKS = 6_000;
    private static final long[] SEEDS = {12345L, -987654321L};

    private record Placement(VoxelDomain domain, List<Shelter.OrientedBox> obstacles) {}

    /**
     * The structure placed as the game places it: anchored at the middle cell of the middle
     * bottom tank (a span: at its box's min tank), mapped into a group engine's frame by the
     * same route {@link TankObstacles#group} and {@link TankShelters#group} take.
     */
    private static Placement place(CosmeticStructure structure, String domainName, Rotation rotation) {
        boolean[][][] occupancy = Scenarios.occupancy(domainName);
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
        int mx = structure.span().isPresent() ? 0 : sx / 2, mz = structure.span().isPresent() ? 0 : sz / 2;
        float ox = sx / 2f - mx, oy = sy / 2f, oz = sz / 2f - mz;
        float anchorX, anchorZ;
        if (structure.span().isPresent()) {
            float[] origin = SpanStructures.buildOrigin(structure, rotation);
            anchorX = origin[0];
            anchorZ = origin[1];
        } else {
            CosmeticGridCell anchor = new CosmeticGridCell(1, 1);
            anchorX = (float) anchor.localX();
            anchorZ = (float) anchor.localZ();
        }
        List<Shelter.OrientedBox> obstacles = TankObstacles.toEngine(TankObstacles.inBlockFrame(
                CosmeticObstacles.derive(structure, ShippedStructures.softTag()), structure.scale(), anchorX, anchorZ, rotation),
                ox, oy, oz, 0f);
        List<Shelter> shelters = new ArrayList<>();
        for (Shelter shelter : TankShelters.inBlockFrame(structure, anchorX, anchorZ, rotation)) {
            shelters.add(TankShelters.toEngine(shelter, ox, oy, oz, 0f));
        }
        VoxelDomain domain = new VoxelDomain(occupancy);
        domain.rebuildShelters(shelters);
        domain.rebuildObstacles(obstacles);
        return new Placement(domain, obstacles);
    }

    /** Just the obstacles of {@link #place}, for {@link ObstacleExport}. */
    static List<Shelter.OrientedBox> placeObstacles(CosmeticStructure structure, String domainName, Rotation rotation) {
        return place(structure, domainName, rotation).obstacles();
    }

    @TestFactory
    List<DynamicTest> swimmersStayOutOfEveryShippedCosmetic() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure structure = e.getValue();
            List<String[]> configs = new ArrayList<>();
            if (structure.span().isPresent()) {
                CosmeticStructure.Span span = structure.span().get();
                String box = span.x() + "x" + span.y() + "x" + span.z();
                configs.add(new String[]{box, "6"});
                configs.add(new String[]{box, "12"});
            } else {
                configs.add(new String[]{"3x1x1", "6"});
                configs.add(new String[]{"3x1x1", "12"});
                configs.add(new String[]{"1x1x1", "6"});
                // Two deep and two high, where a pass-through's far mouth faced the glass closely
                // and pinned its visitors there in EXIT (FlockEngine.throughMouth).
                configs.add(new String[]{"3x2x2", "6"});
            }
            for (String[] config : configs) {
                for (int s = 0; s < SEEDS.length; s++) {
                    long seed = SEEDS[s];
                    Rotation rotation = s % 2 == 0 ? Rotation.NONE : Rotation.CLOCKWISE_90;
                    // A span's box turns with it.
                    String domainName = structure.span().isPresent() && rotation == Rotation.CLOCKWISE_90
                            ? turned(config[0]) : config[0];
                    int n = Integer.parseInt(config[1]);
                    tests.add(DynamicTest.dynamicTest(e.getKey() + " " + domainName + " n=" + n + " seed=" + seed + " " + rotation,
                            () -> run(structure, domainName, rotation, n, seed)));
                }
            }
        }
        return tests;
    }

    private static String turned(String box) {
        String[] d = box.split("x");
        return d[2] + "x" + d[1] + "x" + d[0];
    }

    private static void run(CosmeticStructure structure, String domainName, Rotation rotation, int n, long seed) {
        Placement placement = place(structure, domainName, rotation);
        Tunables t = Tunables.GROUP;
        FlockEngine engine = new FlockEngine(t);
        FishSpec[] specs = Scenarios.visitors(n, seed);
        engine.rebuild(specs, seed, 0f, 20f, placement.domain());
        Metrics m = new Metrics(engine, t, 200);

        float[] winL = new float[n], winY = new float[n], winD = new float[n], windowMax = new float[n];
        float minWindowDisp = Float.MAX_VALUE;
        int inside = 0;
        String firstInside = null;
        for (int tick = 0; tick < TICKS; tick++) {
            if (tick % WINDOW_TICKS == 0) {
                for (int i = 0; i < n; i++) {
                    if (tick > 0 && windowMax[i] < minWindowDisp) minWindowDisp = windowMax[i];
                    winL[i] = engine.posL()[i];
                    winY[i] = engine.posY()[i];
                    winD[i] = engine.posD()[i];
                    windowMax[i] = 0f;
                }
            }
            engine.step();
            m.sample();
            for (int i = 0; i < n; i++) {
                float l = engine.posL()[i], y = engine.posY()[i], d = engine.posD()[i];
                if (engine.shelterUsing(i) < 0) {
                    for (Shelter.OrientedBox box : placement.obstacles()) {
                        if (box.contains(l, y, d)) {
                            inside++;
                            if (firstInside == null) firstInside = "fish " + i + " at tick " + tick + " (" + l + ", " + y + ", " + d + ")";
                            break;
                        }
                    }
                }
                float dx = l - winL[i], dy = y - winY[i], dz = d - winD[i];
                windowMax[i] = Math.max(windowMax[i], (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        // The window the run ends inside is owed its fold too: the loop only folds a window when
        // the next one starts, so without this the last WINDOW_TICKS were the one stretch a fish
        // caught in a pocket could hide in — and the wedge guards are deliberately absent (§12.3.1).
        for (int i = 0; i < n; i++) {
            if (windowMax[i] < minWindowDisp) minWindowDisp = windowMax[i];
        }

        assertEquals(0, inside, "fish-ticks inside a solid obstacle; first: " + firstInside);
        assertEquals(0, m.wallPenetrations(), "wall penetrations");
        assertEquals(0, m.hardClampContacts(), "hard backstop engagements");
        float speedCeiling = t.maxSpeed() * (1f + t.traitJitter());
        assertTrue(m.maxObservedSpeed() <= speedCeiling * SPEED_TOLERANCE, "speed " + m.maxObservedSpeed());
        assertTrue(m.maxObservedAccel() <= t.maxForce() * ACCEL_TOLERANCE, "accel " + m.maxObservedAccel());
        assertTrue(m.maxObservedJerk() <= MAX_JERK, "jerk " + m.maxObservedJerk());
        assertTrue(minWindowDisp >= MIN_WINDOW_DISPLACEMENT,
                "a fish moved only " + minWindowDisp + " blocks in a " + WINDOW_TICKS + "-tick window");
    }

    /** The lone-tank frame is the group frame of a 1×1×1 group: same origin, the item baseline. */
    static {
        if (Math.abs(FishTankBlockEntityRenderer.ITEM_BASELINE_Y - 0.5f) > 1e-6f) {
            throw new AssertionError("a promoted lone tank's frame is no longer a 1x1x1 group's");
        }
    }
}
