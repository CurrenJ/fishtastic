package grill24.fishtastic.school;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.domain.WaterDomain;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The school's motion as the server and every client copy run it: a bobber anchored where a fish
 * can reach it, the member changes a visit makes, and two copies fed the same inputs agreeing.
 */
class FishSchoolSimTest {

    private static final BlockPos ORIGIN = new BlockPos(100, -63, 200);
    private static final float SURFACE = 8f / 9f;
    /** Bluegill at its mean size: 30 cm × 0.89 calibration. */
    private static final float LENGTH = 0.267f;
    private static final UUID A = new UUID(1, 1), B = new UUID(2, 2), C = new UUID(3, 3);

    /** A 13 × 13 pond one block deep, as the live GameTest builds: the shallowest a school gets. */
    private static List<WaterDomain.Column> shallow(int depth) {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                cols.add(new WaterDomain.Column(ORIGIN.getX() + x, ORIGIN.getZ() + z,
                        new float[]{ORIGIN.getY(), ORIGIN.getY() + depth - 1 + SURFACE}, false));
            }
        }
        return cols;
    }

    private static FishSchoolSim school(long seed, List<WaterDomain.Column> water) {
        FishSchoolSim sim = FishSchoolSim.create(new FishSchoolSim.Setup(ORIGIN, water,
                FishSchoolSim.spec(LENGTH, Locomotion.FREE_SWIM, ShelterUse.VISITOR), seed, List.of(
                        new FishSchoolSim.Placement(A, 2.5f, 0.5f, 0.5f),
                        new FishSchoolSim.Placement(B, -1.5f, 0.5f, 0.5f),
                        new FishSchoolSim.Placement(C, 0.5f, 0.5f, 3.5f)), VisitShape.SCHOOL));
        assertNotNull(sim);
        return sim;
    }

    private static void run(FishSchoolSim sim, int steps, Consumer<FishSchoolSim.Event> events) {
        for (int t = 0; t < steps; t++) sim.step(events);
    }

    @Test
    void aFloatingBobberIsAnchoredWhereTheWholeAnchorIsInTheWater() {
        for (long seed = 1; seed <= 6; seed++) {
            FishSchoolSim sim = school(seed, shallow(1));
            run(sim, 30, null);
            // A bobber floats above the head-room a fish keeps under the surface (measured in the
            // live GameTest at 0.75 in one-block water). Anchoring at the first point whose centre
            // was in the water put the anchor's top above it, and the engine refused every send.
            float[] at = sim.anchorPoint(0.5f, 0.75f, 0.5f);
            assertNotNull(at, "seed " + seed);
            assertTrue(at[1] < 0.75f, "the anchor sits below a floating bobber");
            UUID bobber = UUID.randomUUID();
            assertTrue(sim.apply(new FishSchoolSim.Approach(bobber, B, at[0], at[1], at[2], 2f, 8f)),
                    "the engine refused the anchor, seed " + seed);
            boolean[] touched = new boolean[1];
            run(sim, 400, e -> touched[0] |= e.touch() && e.anchor().equals(bobber));
            assertTrue(touched[0], "the sent member never reached its bobber, seed " + seed);
        }
    }

    @Test
    void noAnchorIsOfferedOutOfTheWater() {
        FishSchoolSim sim = school(1, shallow(1));
        assertEquals(null, sim.anchorPoint(30f, 0.75f, 0.5f));
        assertEquals(null, sim.anchorPoint(0.5f, 5f, 0.5f), "too far above the water to hang a hook");
    }

    @Test
    void membersLeaveAndRejoinInOrder() {
        FishSchoolSim sim = school(1, shallow(3));
        run(sim, 20, null);
        assertTrue(sim.apply(new FishSchoolSim.Leave(B)));
        assertEquals(List.of(A, C), sim.members());
        assertFalse(sim.apply(new FishSchoolSim.Leave(B)), "already gone");
        run(sim, 20, null);
        assertTrue(sim.apply(new FishSchoolSim.Rejoin(B, 1f, 1.5f, 1f)));
        assertEquals(List.of(A, C, B), sim.members());
        assertEquals(1f, sim.engine().posL()[2], 1e-6f, "rejoined where it got away");
        assertFalse(sim.apply(new FishSchoolSim.Rejoin(B, 1f, 1.5f, 1f)), "already back");
    }

    @Test
    void withdrawingTheBobberCallsTheFishOff() {
        FishSchoolSim sim = school(2, shallow(3));
        run(sim, 30, null);
        UUID bobber = UUID.randomUUID();
        float[] at = sim.anchorPoint(4.5f, 2.6f, 4.5f);
        assertNotNull(at);
        assertTrue(sim.apply(new FishSchoolSim.Approach(bobber, A, at[0], at[1], at[2], 2f, 8f)));
        assertTrue(sim.errandActive(bobber));
        assertTrue(sim.apply(new FishSchoolSim.Withdraw(bobber)));
        assertFalse(sim.errandActive(bobber));
        assertFalse(sim.hasAnchor(bobber));
        assertFalse(sim.apply(new FishSchoolSim.Withdraw(bobber)), "nothing left to withdraw");
    }

    @Test
    void theWaterChangingKeepsAnErrandUnderWay() {
        FishSchoolSim sim = school(3, shallow(3));
        run(sim, 30, null);
        UUID bobber = UUID.randomUUID();
        float[] at = sim.anchorPoint(4.5f, 2.6f, 4.5f);
        assertTrue(sim.apply(new FishSchoolSim.Approach(bobber, B, at[0], at[1], at[2], 2f, 8f)));
        // A block placed in the far corner of the pond.
        List<WaterDomain.Column> water = new ArrayList<>(shallow(3));
        water.removeIf(c -> c.worldX() == ORIGIN.getX() - 6 && c.worldZ() == ORIGIN.getZ() - 6);
        assertTrue(sim.apply(new FishSchoolSim.Water(water)));
        assertTrue(sim.errandActive(bobber), "a domain rebuild must not call the fish off");
    }

    @Test
    void twoCopiesFedTheSameInputsAgreeExactly() {
        FishSchoolSim server = school(0x5C001L, shallow(3));
        FishSchoolSim client = school(0x5C001L, shallow(3));
        UUID bobber = UUID.randomUUID();
        List<WaterDomain.Column> smaller = new ArrayList<>(shallow(3));
        smaller.removeIf(c -> c.worldX() == ORIGIN.getX() + 6);
        for (int t = 0; t < 3000; t++) {
            List<FishSchoolSim.Input> inputs = switch (t) {
                case 200 -> {
                    float[] at = server.anchorPoint(4.5f, 2.6f, 4.5f);
                    yield List.of(new FishSchoolSim.Approach(bobber, C, at[0], at[1], at[2], 2f, 6f));
                }
                case 500 -> List.of(new FishSchoolSim.Leave(A));
                case 900 -> List.of(new FishSchoolSim.Water(smaller));
                case 1400 -> List.of(new FishSchoolSim.Withdraw(bobber), new FishSchoolSim.Rejoin(A, 1f, 1.5f, 1f));
                default -> List.of();
            };
            for (FishSchoolSim.Input input : inputs) assertEquals(server.apply(input), client.apply(input), "step " + t);
            server.step(null);
            client.step(null);
            assertEquals(server.members(), client.members());
            for (int i = 0; i < server.members().size(); i++) {
                assertEquals(server.engine().posL()[i], client.engine().posL()[i], 0f, "step " + t);
                assertEquals(server.engine().posY()[i], client.engine().posY()[i], 0f, "step " + t);
                assertEquals(server.engine().posD()[i], client.engine().posD()[i], 0f, "step " + t);
            }
        }
    }
}
