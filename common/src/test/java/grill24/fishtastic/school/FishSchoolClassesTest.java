package grill24.fishtastic.school;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.domain.WaterDomain;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the non-swimming classes take a bobber (docs/in-world-fish-encounters-design-spec.md §4):
 * a crawler walks the floor to beneath it (the bottom bite), a drifter is carried to it, an eel
 * takes one only over its own burrow and ducks from a player who rushes it. All of it lockstep.
 */
class FishSchoolClassesTest {

    private static final BlockPos ORIGIN = new BlockPos(100, -63, 200);
    private static final float SURFACE = 8f / 9f;
    private static final UUID A = new UUID(1, 1), B = new UUID(2, 2), HOOK = new UUID(9, 9);

    private static List<WaterDomain.Column> pond(int size, int depth) {
        List<WaterDomain.Column> cols = new ArrayList<>();
        int half = size / 2;
        for (int x = -half; x < size - half; x++) {
            for (int z = -half; z < size - half; z++) {
                cols.add(new WaterDomain.Column(ORIGIN.getX() + x, ORIGIN.getZ() + z,
                        new float[]{ORIGIN.getY(), ORIGIN.getY() + depth - 1 + SURFACE}, false));
            }
        }
        return cols;
    }

    private static FishSchoolSim.Setup setup(Locomotion locomotion, float length, int depth, long seed,
                                             FishSchoolSim.Placement... members) {
        FishSpec spec = FishSchoolSim.spec(length, locomotion, ShelterUse.NONE);
        return new FishSchoolSim.Setup(ORIGIN, pond(13, depth), spec, seed, List.of(members), VisitShape.LOOSE_GROUP);
    }

    /** Steps until {@code anchor} is touched, at most {@code limit} steps; the step it happened, or -1. */
    private static long stepUntilTouch(FishSchoolSim sim, UUID anchor, int limit) {
        long[] touchedAt = {-1};
        for (int s = 0; s < limit && touchedAt[0] < 0; s++) {
            sim.step(e -> {
                if (e.touch() && e.anchor().equals(anchor)) touchedAt[0] = sim.steps();
            });
        }
        return touchedAt[0];
    }

    @Test
    void aCrawlerWalksTheFloorToBeneathTheBobberAndTakesIt() {
        FishSchoolSim.Setup setup = setup(Locomotion.BENTHIC, 0.3f, 4, 5L,
                new FishSchoolSim.Placement(A, -4.5f, 1.5f, -4.5f));
        FishSchoolSim sim = FishSchoolSim.create(setup);
        assertNotNull(sim);
        assertEquals(0f, sim.engine().posY()[0], 1e-4f, "a crawler is placed on the floor, not mid-water");

        float[] goal = sim.goalFor(A, 3.5f, 3.9f, 3.5f);
        assertNotNull(goal, "a bobber over a floor 4 blocks down is in reach of a bottom bite");
        assertEquals(0f, goal[1], 1e-4f, "the bottom bite's goal is on the floor beneath the bobber");
        assertTrue(sim.apply(new FishSchoolSim.Approach(HOOK, A, goal[0], goal[1], goal[2], 2.5f, 3f)));
        assertTrue(sim.errandActive(HOOK));

        long touched = stepUntilTouch(sim, HOOK, 1200);
        assertTrue(touched > 0, "the crawler must reach beneath the bobber within the doubled approach time");
        assertEquals(0f, sim.engine().posY()[0], 1e-4f, "it never leaves the floor");
        float dl = sim.engine().posL()[0] - goal[0], dd = sim.engine().posD()[0] - goal[2];
        assertTrue(dl * dl + dd * dd <= 0.3f * 0.3f, "it stops beneath the bobber");

        // It holds there for its hold time, then lets go and wanders again.
        for (int s = 0; s < 61 && sim.errandActive(HOOK); s++) sim.step(null);
        assertTrue(!sim.errandActive(HOOK), "the errand ends once the hold is over");
    }

    @Test
    void aBottomBiteNeedsAFloorWithinReach() {
        FishSchoolSim shallow = FishSchoolSim.create(setup(Locomotion.BENTHIC, 0.3f, 4, 1L,
                new FishSchoolSim.Placement(A, 0.5f, 0.5f, 0.5f)));
        FishSchoolSim deep = FishSchoolSim.create(setup(Locomotion.BENTHIC, 0.3f, 11, 1L,
                new FishSchoolSim.Placement(A, 0.5f, 0.5f, 0.5f)));
        assertNotNull(shallow);
        assertNotNull(deep);
        assertNotNull(shallow.goalFor(A, 2.5f, 3.9f, 2.5f));
        assertNull(deep.goalFor(A, 2.5f, 10.9f, 2.5f), "a floor 11 blocks down is past the 8-block bottom bite");
    }

    @Test
    void aDrifterIsCarriedToTheBait() {
        FishSchoolSim sim = FishSchoolSim.create(setup(Locomotion.DRIFT, 0.4f, 5, 3L,
                new FishSchoolSim.Placement(A, -3.5f, 2.0f, -3.5f)));
        assertNotNull(sim);
        float[] goal = sim.goalFor(A, 3.5f, 4.9f, 3.5f);
        assertNotNull(goal, "a drifter goes to the point under the bobber, as a swimmer would");
        assertTrue(sim.apply(new FishSchoolSim.Approach(HOOK, A, goal[0], goal[1], goal[2], 2.5f, 3f)));
        long touched = stepUntilTouch(sim, HOOK, 1800);
        assertTrue(touched > 0, "the drifter must be carried to the bait within the tripled approach time");
    }

    @Test
    void anEelTakesOnlyABobberOverItsBurrowAndOnlyWhenOut() {
        FishSchoolSim sim = FishSchoolSim.create(setup(Locomotion.ANCHORED, 0.5f, 3, 4L,
                new FishSchoolSim.Placement(A, 0.5f, 0.5f, 0.5f)));
        assertNotNull(sim);
        for (int s = 0; s < 20; s++) sim.step(null);
        float l = sim.engine().posL()[0], d = sim.engine().posD()[0];
        assertNull(sim.goalFor(A, l + 3f, 2.9f, d), "a bobber 3 blocks off is out of an eel's reach");
        float[] goal = sim.goalFor(A, l + 1f, 2.9f, d);
        assertNotNull(goal, "a bobber within 1.5 blocks of an emerged eel is taken");
        assertTrue(sim.apply(new FishSchoolSim.Approach(HOOK, A, goal[0], goal[1], goal[2], 2.5f, 3f)));
        assertTrue(stepUntilTouch(sim, HOOK, 5) > 0, "an emerged eel takes the bobber at once");
        assertEquals(l, sim.engine().posL()[0], "an eel never leaves its burrow");
    }

    @Test
    void anEelDucksFromSomeoneRushingIt() {
        FishSchoolSim sim = FishSchoolSim.create(setup(Locomotion.ANCHORED, 0.5f, 3, 4L,
                new FishSchoolSim.Placement(A, 0.5f, 0.5f, 0.5f)));
        assertNotNull(sim);
        for (int s = 0; s < 20; s++) sim.step(null);
        float l = sim.engine().posL()[0], y = sim.engine().posY()[0], d = sim.engine().posD()[0];
        sim.apply(new FishSchoolSim.Watcher(true, l + 1f, y + 1f, d));
        for (int s = 0; s < 15; s++) sim.step(null);
        assertTrue(sim.engine().shapeDrive(0) > 0.3f, "a rushed eel withdraws into its burrow");
        assertNull(sim.goalFor(A, l + 1f, 2.9f, d), "a hidden eel takes no bobber");
    }

    @Test
    void twoCopiesOfEachClassAgreeThroughSeeksAndWatchers() {
        for (Locomotion locomotion : new Locomotion[]{Locomotion.BENTHIC, Locomotion.DRIFT, Locomotion.ANCHORED}) {
            FishSchoolSim.Setup setup = setup(locomotion, 0.35f, 4, 11L,
                    new FishSchoolSim.Placement(A, -3.5f, 1.5f, -2.5f),
                    new FishSchoolSim.Placement(B, 3.5f, 1.5f, 2.5f));
            FishSchoolSim server = FishSchoolSim.create(setup), client = FishSchoolSim.create(setup);
            assertNotNull(server);
            assertNotNull(client);
            for (int s = 0; s < 400; s++) {
                if (s == 30) {
                    float[] goal = server.goalFor(A, server.engine().posL()[0] + 1f, 3.9f, server.engine().posD()[0]);
                    if (goal != null) {
                        var approach = new FishSchoolSim.Approach(HOOK, A, goal[0], goal[1], goal[2], 2.5f, 2f);
                        assertEquals(server.apply(approach), client.apply(approach));
                    }
                }
                if (s == 120) {
                    var watcher = new FishSchoolSim.Watcher(true, 0.5f, 3f, 0.5f);
                    server.apply(watcher);
                    client.apply(watcher);
                }
                if (s == 200) {
                    server.apply(new FishSchoolSim.Leave(B));
                    client.apply(new FishSchoolSim.Leave(B));
                }
                server.step(null);
                client.step(null);
                assertEquals(server.checksum(), client.checksum(), locomotion + " copies diverged at step " + s);
            }
        }
    }

    @Test
    void aWatcherSurvivesTheWire() {
        ByteBuf buf = Unpooled.buffer();
        var sent = new FishSchoolSim.Watcher(true, 1.25f, -0.5f, 3.75f);
        FishSchoolWire.INPUT.encode(buf, sent);
        assertEquals(sent, FishSchoolWire.INPUT.decode(buf));
        var away = new FishSchoolSim.Watcher(false, 0f, 0f, 0f);
        FishSchoolWire.INPUT.encode(buf, away);
        assertEquals(away, FishSchoolWire.INPUT.decode(buf));
    }
}
