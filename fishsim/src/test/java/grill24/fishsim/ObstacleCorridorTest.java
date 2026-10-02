package grill24.fishsim;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Metrics;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cosmetic dropped across a shelter's doorway closes that doorway, and only that one
 * (docs/fish-shelters.md §12.3): a post standing in front of the Clay Pipe's high mouth means no
 * fish goes in that way and none passes through to leave that way, while the low mouth carries on.
 * Nobody is ever inside the post, and the hard backstop never engages. The same tank without the
 * post is the control: there, fish do use the high mouth both ways.
 */
class ObstacleCorridorTest {

    private static final long[] SEEDS = {1, 2, 3, 4};

    /** Entries by the high mouth, entries by the low one, exits by the high one, fish-ticks inside {@code post}. */
    private static int[] visits(VoxelDomain domain, Shelter.OrientedBox post) {
        int[] out = new int[4];
        for (long seed : SEEDS) {
            FlockEngine engine = new FlockEngine(Tunables.GROUP);
            engine.rebuild(Scenarios.loaches(10, seed), seed, 0f, 20f, domain);
            Metrics m = new Metrics(engine, Tunables.GROUP, 200);
            int[] state = new int[engine.count()];
            for (int tick = 0; tick < 6000; tick++) {
                engine.step();
                m.sample();
                for (int i = 0; i < engine.count(); i++) {
                    int now = engine.shelterState(i);
                    if (state[i] == FlockEngine.SHELTER_APPROACH && now == FlockEngine.SHELTER_ENTER) {
                        out[engine.shelterMouth(i) == 0 ? 0 : 1]++;
                    }
                    if (state[i] == FlockEngine.SHELTER_EXIT && now == FlockEngine.SHELTER_ROAMING && engine.shelterMouth(i) == 0) {
                        out[2]++;
                    }
                    if (post.contains(engine.posL()[i], engine.posY()[i], engine.posD()[i])) out[3]++;
                    state[i] = now;
                }
            }
            assertEquals(0, m.hardClampContacts(), "seed " + seed + " hard backstop engagements");
        }
        return out;
    }

    @Test
    void aPostAcrossAMouthClosesThatMouthOnly() {
        VoxelDomain domain = Scenarios.domain("3x1x1+pipe");
        Shelter.Mouth high = domain.shelters().get(0).mouths().get(0);
        assertTrue(high.normalL() < 0f, "mouth 0 is the high-lateral end");
        // A post a little way out from the high mouth, square across its axis.
        float floor = -0.5f + Scenarios.SAND_SURFACE;
        Shelter.OrientedBox post = Shelter.OrientedBox.ofBounds(
                high.centerL() + 0.10f, floor, high.centerD() - 0.08f,
                high.centerL() + 0.16f, floor + 0.35f, high.centerD() + 0.08f);

        int[] open = visits(domain, post);
        assertTrue(open[0] > 0 && open[2] > 0, "control: the high mouth is used both ways without the post");

        domain.rebuildObstacles(List.of(post));
        int[] blocked = visits(domain, post);
        assertEquals(0, blocked[0], "fish went in through the blocked mouth");
        assertEquals(0, blocked[2], "fish passed through to the blocked mouth");
        assertTrue(blocked[1] > 10, "the open mouth fell out of use: " + blocked[1] + " entries");
        assertEquals(0, blocked[3], "fish-ticks inside the post");
    }
}
