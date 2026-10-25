package grill24.fishtastic.school;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.domain.WaterDomain;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
 * A client's copy against the server it copies: joined mid-visit, fed inputs as they happen and
 * fingerprints every 10 ticks, it must reach the server's step under its replay budget and then
 * agree with the server at every fingerprint — and put itself right when an input arrives late.
 */
class FishSchoolReplicaTest {

    private static final BlockPos ORIGIN = new BlockPos(-3000, 60, 4000);
    private static final UUID A = new UUID(1, 1), B = new UUID(2, 2), C = new UUID(3, 3), D = new UUID(4, 4);
    private static final UUID BOBBER = new UUID(9, 9);

    private static List<WaterDomain.Column> pond(int maxX) {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z < 8; z++) {
                cols.add(new WaterDomain.Column(ORIGIN.getX() + x, ORIGIN.getZ() + z,
                        new float[]{ORIGIN.getY() - 2, ORIGIN.getY() + 8f / 9f}, false));
            }
        }
        return cols;
    }

    private static FishSchoolSim.Setup setup() {
        return new FishSchoolSim.Setup(ORIGIN, pond(7), FishSchoolSim.spec(0.267f, Locomotion.FREE_SWIM, ShelterUse.VISITOR),
                0xC0FFEEL, List.of(
                        new FishSchoolSim.Placement(A, 1.5f, -0.5f, 1.5f),
                        new FishSchoolSim.Placement(B, 5.5f, -0.5f, 1.5f),
                        new FishSchoolSim.Placement(C, 1.5f, -0.5f, 5.5f),
                        new FishSchoolSim.Placement(D, 5.5f, -0.5f, 5.5f)));
    }

    /** The server side of the visit: its sim, its input log, and its fingerprint after every step. */
    private static final class Server {
        final FishSchoolSim sim = FishSchoolSim.create(setup());
        final List<FishSchoolSim.Stamped> log = new ArrayList<>();
        final Map<Long, Integer> checksums = new HashMap<>();

        /** One game tick: step, fingerprint, then the inputs the visit makes at this tick. */
        List<FishSchoolSim.Stamped> tick(int t) {
            sim.step(null);
            checksums.put(sim.steps(), sim.checksum());
            List<FishSchoolSim.Input> inputs = switch (t) {
                case 400 -> {
                    float[] at = sim.anchorPoint(6f, 0.75f, 6f);
                    yield List.of(new FishSchoolSim.Approach(BOBBER, A, at[0], at[1], at[2], 2f, 8f));
                }
                case 900 -> List.of(new FishSchoolSim.Leave(A), new FishSchoolSim.Withdraw(BOBBER));
                case 1300 -> List.of(new FishSchoolSim.Water(pond(6)));
                case 1700 -> List.of(new FishSchoolSim.Rejoin(A, 2f, -0.4f, 2f));
                case 2300 -> List.of(new FishSchoolSim.Leave(D));
                default -> List.of();
            };
            List<FishSchoolSim.Stamped> out = new ArrayList<>();
            for (FishSchoolSim.Input input : inputs) {
                FishSchoolSim.Stamped stamped = new FishSchoolSim.Stamped(sim.steps(), input);
                log.add(stamped);
                sim.apply(input);
                out.add(stamped);
            }
            return out;
        }
    }

    @Test
    void aCopyJoinedMidVisitCatchesUpAndAgreesAtEveryFingerprint() {
        Server server = new Server();
        for (int t = 0; t < 1200; t++) server.tick(t);
        // The setup packet: the setup, every input so far, the server's step.
        FishSchoolReplica copy = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.copyOf(server.log),
                server.sim.steps());
        int caughtUpAfter = -1, checked = 0;
        for (int t = 1200; t < 3000; t++) {
            List<FishSchoolSim.Stamped> sent = server.tick(t);
            copy.addInputs(sent);
            if (t % 10 == 0) {
                copy.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
                checked++;
            }
            copy.tick();
            if (caughtUpAfter < 0 && copy.caughtUp()) caughtUpAfter = t - 1200;
        }
        assertTrue(caughtUpAfter >= 0 && caughtUpAfter <= 2, "caught up after " + caughtUpAfter + " ticks");
        assertEquals(0, copy.mismatches(), "the copy disagreed with the server");
        assertEquals(0, copy.replays());
        assertTrue(checked > 150);
        long behind = server.sim.steps() - copy.sim().steps();
        assertTrue(behind >= FishSchoolReplica.DELAY_TICKS - 1 && behind <= FishSchoolReplica.DELAY_TICKS + 1,
                "the copy runs " + behind + " steps behind");
        assertEquals(server.checksums.get(copy.sim().steps()), copy.sim().checksum());
        assertEquals(server.sim.members(), copy.sim().members());
    }

    @Test
    void aSpentReplayBudgetSlowsTheCatchUpButKeepsItExact() {
        Server server = new Server();
        for (int t = 0; t < 1200; t++) server.tick(t);
        FishSchoolReplica copy = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.copyOf(server.log),
                server.sim.steps());
        int caughtUpAfter = -1;
        for (int t = 1200; t < 1400; t++) {
            List<FishSchoolSim.Stamped> sent = server.tick(t);
            copy.addInputs(sent);
            if (t % 10 == 0) copy.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
            // Other copies already used this tick's budget: only the guaranteed minimum is taken.
            int taken = copy.tick(null, System.nanoTime() - 1);
            if (!copy.caughtUp()) assertEquals(FishSchoolReplica.MIN_REPLAY_STEPS_PER_TICK, taken);
            if (caughtUpAfter < 0 && copy.caughtUp()) caughtUpAfter = t - 1200;
        }
        // 1200 steps behind, gaining 31 a tick: caught up in ~39 ticks, not 1.
        assertTrue(caughtUpAfter > 30 && caughtUpAfter < 50, "caught up after " + caughtUpAfter + " ticks");
        assertEquals(0, copy.mismatches());
        assertEquals(0, copy.replays());
        assertEquals(server.checksums.get(copy.sim().steps()), copy.sim().checksum());
    }

    @Test
    void aLateInputIsReplayedIntoPlace() {
        Server server = new Server();
        FishSchoolReplica copy = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.of(), 0);
        List<FishSchoolSim.Stamped> held = new ArrayList<>();
        int heldUntil = -1;
        for (int t = 0; t < 1400; t++) {
            List<FishSchoolSim.Stamped> sent = server.tick(t);
            if (t == 900) {
                // A lag spike: the session's Leave and Withdraw reach the client 8 ticks late.
                held.addAll(sent);
                heldUntil = t + 8;
            } else {
                copy.addInputs(sent);
            }
            if (t == heldUntil) copy.addInputs(held);
            if (t % 10 == 0) copy.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
            copy.tick();
        }
        assertTrue(copy.replays() >= 1, "a late input must make the copy start over");
        assertTrue(copy.caughtUp());
        assertEquals(server.checksums.get(copy.sim().steps()), copy.sim().checksum(),
                "after the replay the copy is the server's again");
    }

    @Test
    void aStalledClientReplaysForward() {
        Server server = new Server();
        FishSchoolReplica copy = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.of(), 0);
        for (int t = 0; t < 600; t++) {
            copy.addInputs(server.tick(t));
            if (t % 10 == 0) copy.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
            // The client freezes for 5 seconds (no ticks) halfway.
            if (t < 200 || t > 300) copy.tick();
        }
        assertTrue(copy.caughtUp());
        long behind = server.sim.steps() - copy.sim().steps();
        assertTrue(behind <= FishSchoolReplica.DELAY_TICKS + 1, behind + " steps behind after the stall");
        assertEquals(0, copy.mismatches());
    }

    @Test
    void onlyALiveCopyReportsTheTouch() {
        Server server = new Server();
        FishSchoolReplica live = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.of(), 0);
        int[] liveTouches = {0}, lateTouches = {0};
        FishSchoolReplica late = null;
        int serverTouches = 0;
        for (int t = 0; t < 1400; t++) {
            List<FishSchoolSim.Stamped> sent = server.tick(t);
            live.addInputs(sent);
            if (late != null) late.addInputs(sent);
            if (t == 1000) {
                // Joins after the bite at the bobber: replaying it must not show it again.
                late = new FishSchoolReplica(UUID.randomUUID(), 1, setup(), List.copyOf(server.log), server.sim.steps());
            }
            if (t % 10 == 0) {
                live.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
                if (late != null) late.observe(server.sim.steps(), server.checksums.get(server.sim.steps()));
            }
            live.tick(e -> { if (e.touch()) liveTouches[0]++; });
            if (late != null) late.tick(e -> { if (e.touch()) lateTouches[0]++; });
        }
        FishSchoolSim replay = FishSchoolSim.create(setup());
        int[] touches = {0};
        int next = 0;
        for (long step = 0; step < 1000; step++) {
            while (next < server.log.size() && server.log.get(next).step() == replay.steps()) replay.apply(server.log.get(next++).input());
            replay.step(e -> { if (e.touch()) touches[0]++; });
        }
        assertTrue(touches[0] >= 1, "the scripted approach must reach its bobber, or the test proves nothing");
        assertEquals(touches[0], liveTouches[0], "a live copy reports each touch once");
        assertEquals(0, lateTouches[0], "a copy catching up must not replay past bites");
    }

    @Test
    void theWireKeepsEveryBit() {
        Server server = new Server();
        for (int t = 0; t < 2400; t++) server.tick(t);
        ByteBuf buf = Unpooled.buffer();
        FishSchoolWire.SETUP.encode(buf, setup());
        FishSchoolWire.writeList(buf, server.log, FishSchoolWire.STAMPED);
        FishSchoolSim.Setup setup = FishSchoolWire.SETUP.decode(buf);
        List<FishSchoolSim.Stamped> log = FishSchoolWire.readList(buf, FishSchoolWire.STAMPED, FishSchoolWire.MAX_INPUTS);
        assertEquals(0, buf.readableBytes());

        FishSchoolSim.Setup original = setup();
        assertEquals(original.origin(), setup.origin());
        assertEquals(original.spec(), setup.spec());
        assertEquals(original.seed(), setup.seed());
        assertEquals(original.members(), setup.members());
        for (int i = 0; i < original.columns().size(); i++) {
            assertArrayEquals(original.columns().get(i).spans(), setup.columns().get(i).spans());
        }
        assertEquals(server.log.size(), log.size());
        for (int i = 0; i < log.size(); i++) {
            assertEquals(server.log.get(i).step(), log.get(i).step());
            if (!(log.get(i).input() instanceof FishSchoolSim.Water)) {
                assertEquals(server.log.get(i).input(), log.get(i).input(), "input " + i);
            }
        }
        // And a copy built from what came off the wire replays to the server exactly.
        FishSchoolReplica copy = new FishSchoolReplica(UUID.randomUUID(), 1, setup, log, server.sim.steps());
        copy.tick();
        assertNotNull(copy.sim());
        assertEquals(server.checksums.get(copy.sim().steps()), copy.sim().checksum());
    }
}
