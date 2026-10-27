package grill24.fishtastic.school;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.domain.WaterDomain;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A surface jump under lockstep replay: its commit, arc and landing hand-off all happen inside the
 * sim's step from shared state, so a copy that joins at any point of it, or rebuilds in the middle
 * of it, must reach the server's very state and draw the very same pose.
 */
class FishSchoolJumpReplayTest {

    private static final BlockPos ORIGIN = new BlockPos(-3000, 60, 4000);
    private static final UUID A = new UUID(1, 1), B = new UUID(2, 2), C = new UUID(3, 3);
    private static final UUID GOAL = new UUID(7, 7);
    private static final int GOAL_AT = 60;

    private static FishSchoolSim.Setup setup() {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 9; z++) {
                cols.add(new WaterDomain.Column(ORIGIN.getX() + x, ORIGIN.getZ() + z,
                        new float[]{ORIGIN.getY() - 3, ORIGIN.getY() + 8f / 9f}, false));
            }
        }
        return new FishSchoolSim.Setup(ORIGIN, cols, FishSchoolSim.spec(0.267f, Locomotion.FREE_SWIM, ShelterUse.VISITOR),
                0xB0A7L, List.of(
                        new FishSchoolSim.Placement(A, 2.5f, -1.5f, 2.5f),
                        new FishSchoolSim.Placement(B, 7.5f, -1.5f, 2.5f),
                        new FishSchoolSim.Placement(C, 2.5f, -1.5f, 7.5f)), VisitShape.SCHOOL);
    }

    /** The server: its sim, input log, fingerprint after every step, and its jump's milestones. */
    private static final class Server {
        final FishSchoolSim sim = FishSchoolSim.create(setup());
        final List<FishSchoolSim.Stamped> log = new ArrayList<>();
        final Map<Long, Integer> checksums = new HashMap<>();
        long committedAt = -1, landedAt = -1;

        List<FishSchoolSim.Stamped> tick(int t) {
            sim.step(null);
            checksums.put(sim.steps(), sim.checksum());
            if (committedAt < 0 && sim.goalStarted(GOAL)) committedAt = sim.steps();
            if (landedAt < 0 && sim.goalCompleted(GOAL)) landedAt = sim.steps();
            if (t != GOAL_AT) return List.of();
            float[] at = sim.anchorPoint(6.5f, sim.domain().surfaceAt(6.5f, 6.5f), 6.5f);
            assertNotNull(at);
            FishSchoolSim.Input input = new FishSchoolSim.SurfaceGoal(GOAL, A, at[0], at[1], at[2], 64, 0.5f);
            FishSchoolSim.Stamped stamped = new FishSchoolSim.Stamped(sim.steps(), input);
            assertTrue(sim.apply(input), "the server's goal was refused");
            log.add(stamped);
            return List.of(stamped);
        }
    }

    /** Runs a server through the whole jump once, to learn when it commits and lands. */
    private static Server rehearse() {
        Server server = new Server();
        for (int t = 0; t < 900 && server.landedAt < 0; t++) server.tick(t);
        assertTrue(server.committedAt > 0 && server.landedAt > server.committedAt,
                "the jump never ran: commit " + server.committedAt + " land " + server.landedAt);
        return server;
    }

    @Test
    void aCopyJoiningAtAnyPointOfAJumpAgreesWithTheServer() {
        Server plan = rehearse();
        long commit = plan.committedAt, land = plan.landedAt, span = land - commit;
        // Before the goal, swimming up, mid run-in, mid flight, mid glide, just after landing.
        long[] joins = {GOAL_AT - 20, (GOAL_AT + commit) / 2, commit + 3, commit + span / 2, land - 4, land + 5};
        for (long join : joins) {
            Server server = new Server();
            int t = 0;
            for (; t < join; t++) server.tick(t);
            FishSchoolReplica copy = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.copyOf(server.log),
                    server.sim.steps());
            float[] serverPose = new float[7], copyPose = new float[7];
            int compared = 0;
            for (; t < land + 80; t++) {
                copy.addInputs(server.tick(t));
                if (t % 10 == 0) copy.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
                copy.tick();
                FishSchoolSim mine = copy.sim();
                if (!copy.caughtUp() || mine == null) continue;
                assertEquals(server.checksums.get(mine.steps()), mine.checksum(),
                        "joined at " + join + ", diverged at step " + mine.steps());
                compared++;
            }
            assertEquals(0, copy.mismatches(), "joined at " + join);
            assertEquals(0, copy.replays(), "joined at " + join);
            assertTrue(compared > 50, "joined at " + join + ": compared only " + compared);
            // The drawn pose follows from the state: a copy on the same step draws the same fish.
            FishSchoolSim twin = replayTo(server, copy.sim().steps());
            for (UUID member : twin.members()) {
                boolean a = twin.surfaceJumpPose(member, 0.5f, serverPose);
                boolean b = copy.sim().surfaceJumpPose(member, 0.5f, copyPose);
                assertEquals(a, b);
                if (a) assertArrayEquals(serverPose, copyPose, 0f);
            }
        }
    }

    @Test
    void aCopyRebuiltInTheMiddleOfAJumpDrawsEveryStepOfItExactly() {
        Server plan = rehearse();
        long midFlight = (plan.committedAt + plan.landedAt) / 2;
        Server server = new Server();
        FishSchoolReplica copy = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.of(), 0);
        // The goal reaches this client late, mid-jump: it must rebuild and replay the whole jump.
        List<FishSchoolSim.Stamped> held = new ArrayList<>();
        FishSchoolSim reference = FishSchoolSim.create(setup());
        float[] expected = new float[7], actual = new float[7];
        int posesCompared = 0;
        for (int t = 0; t < plan.landedAt + 60; t++) {
            List<FishSchoolSim.Stamped> sent = server.tick(t);
            if (t == GOAL_AT) held.addAll(sent);
            else copy.addInputs(sent);
            if (t == midFlight) copy.addInputs(held);
            if (t % 10 == 0) copy.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
            copy.tick();
            FishSchoolSim mine = copy.sim();
            if (!copy.caughtUp() || mine == null || t < midFlight + 5) continue;
            // An independent copy fed every input on time, stepped to the same step, is the truth.
            while (reference.steps() < mine.steps()) {
                for (FishSchoolSim.Stamped s : server.log) if (s.step() == reference.steps()) reference.apply(s.input());
                reference.step(null);
            }
            assertEquals(reference.checksum(), mine.checksum(), "step " + mine.steps());
            if (reference.surfaceJumpPose(A, 0.25f, expected)) {
                assertTrue(mine.surfaceJumpPose(A, 0.25f, actual));
                assertArrayEquals(expected, actual, 0f, "pose at step " + mine.steps());
                posesCompared++;
            }
        }
        assertTrue(copy.replays() >= 1, "the late goal must make the copy start over");
        assertTrue(posesCompared > 5, "the replayed jump was compared only " + posesCompared + " steps");
        assertTrue(server.sim.goalCompleted(GOAL));
        assertEquals(server.checksums.get(copy.sim().steps()), copy.sim().checksum());
    }

    /** A fresh sim fed the server's log on time, stepped to {@code step}. */
    private static FishSchoolSim replayTo(Server server, long step) {
        FishSchoolSim sim = FishSchoolSim.create(setup());
        while (sim.steps() < step) {
            for (FishSchoolSim.Stamped s : server.log) if (s.step() == sim.steps()) sim.apply(s.input());
            sim.step(null);
        }
        return sim;
    }
}
