package grill24.fishsim;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obstacles are binned per block so a fish checks only those near it (docs/fish-shelters.md
 * §12.3), which is meant to be a pure optimisation: a box outside a fish's bin is beyond its
 * avoidance margin and adds nothing. This asserts it to the bit — every fish's position, every
 * tick, identical with the bins on and with every obstacle in one bin — in the spirit of
 * {@code SpatialIndexEquivalenceTest}. A shelter is in the mix, so the scatter, staging and
 * corridor checks that read the bins are covered too.
 */
class ObstacleBinningEquivalenceTest {

    /** A scatter of rocks and posts on the sand of a group, a few reaching up to the lid. */
    static List<Shelter.OrientedBox> rubble(boolean[][][] occupancy, long seed) {
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
        float floor = -sy / 2f + Scenarios.SAND_SURFACE;
        Random r = new Random(seed);
        List<Shelter.OrientedBox> boxes = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            float w = 0.03f + r.nextFloat() * 0.15f, d = 0.03f + r.nextFloat() * 0.15f;
            float h = r.nextFloat() < 0.2f ? sy - 0.2f : 0.05f + r.nextFloat() * 0.4f;
            float l = -sx / 2f + 0.2f + r.nextFloat() * (sx - 0.4f - w);
            float dd = -sz / 2f + 0.2f + r.nextFloat() * (sz - 0.4f - d);
            boxes.add(Shelter.OrientedBox.ofBounds(l, floor, dd, l + w, floor + h, dd + d));
        }
        return boxes;
    }

    @Test
    void binningChangesNothing() {
        VoxelDomain domainA = Scenarios.domain("4x2x2+logpair");
        VoxelDomain domainB = Scenarios.domain("4x2x2+logpair");
        List<Shelter.OrientedBox> boxes = new ArrayList<>(rubble(domainA.occupancy(), 3L));
        // Keep the rubble clear of the logs, so the shelters stay in play.
        boxes.removeIf(b -> domainA.shelters().stream().anyMatch(s -> Math.abs(b.centerL() - s.hull().centerL()) < 0.6f
                && Math.abs(b.centerD() - s.hull().centerD()) < 0.6f));
        domainA.rebuildObstacles(boxes);
        domainB.rebuildObstacles(boxes);

        FlockEngine binned = new FlockEngine(Tunables.GROUP);
        FlockEngine flat = new FlockEngine(Tunables.GROUP);
        flat.setObstacleBinningEnabled(false);
        binned.rebuild(Scenarios.visitors(25, 11L), 11L, 0f, 20f, domainA);
        flat.rebuild(Scenarios.visitors(25, 11L), 11L, 0f, 20f, domainB);

        int visits = 0;
        for (int tick = 0; tick < 4000; tick++) {
            binned.step();
            flat.step();
            for (int i = 0; i < binned.count(); i++) {
                assertEquals(Float.floatToRawIntBits(flat.posL()[i]), Float.floatToRawIntBits(binned.posL()[i]), "tick " + tick + " fish " + i + " L");
                assertEquals(Float.floatToRawIntBits(flat.posY()[i]), Float.floatToRawIntBits(binned.posY()[i]), "tick " + tick + " fish " + i + " Y");
                assertEquals(Float.floatToRawIntBits(flat.posD()[i]), Float.floatToRawIntBits(binned.posD()[i]), "tick " + tick + " fish " + i + " D");
                if (binned.shelterState(i) == FlockEngine.SHELTER_ENTER) visits++;
            }
        }
        assertTrue(visits > 0, "no fish ever visited a log, so the shelter paths went untested");
    }
}
