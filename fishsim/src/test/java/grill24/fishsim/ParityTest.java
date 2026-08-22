package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Task 3's gate (docs/fish-sim-engine-handoff.md): {@link FlockEngine} must produce
 * <b>bitwise-identical</b> trajectories to {@link LegacyStepReference} — the frozen copy of the
 * pre-extraction float math — across a matrix of fish counts, seeds, and swarm parameters.
 * Equality is asserted on raw float bits, not within an epsilon: any reassociated expression or
 * "cleaned up" operation in the port shows up here immediately.
 */
class ParityTest {

    /** One test scenario: the inputs both sims are driven with. */
    record Scenario(String name, int fishCount, long blockPosHash, float rotation,
                    int depthLayers, float xzSpread, float yRange, float rotationJitter,
                    int ticks, long specSeed) {

        FishSpec[] specs() {
            // Deterministic mixed population: varied lengths (some failing the size gate:
            // 2.5 * length > 0.7 hovers), some hover-only species, random mirror flags.
            Random r = new Random(specSeed);
            FishSpec[] specs = new FishSpec[fishCount];
            for (int i = 0; i < fishCount; i++) {
                float length = 0.06f + r.nextFloat() * 0.34f; // 0.06 .. 0.40 → mix of swim/hover
                boolean canSwim = r.nextInt(5) != 0;          // ~20% floor-anchored/hover species
                boolean mirrored = r.nextBoolean();
                specs[i] = new FishSpec(length, canSwim, mirrored, 0);
            }
            return specs;
        }
    }

    private static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();
        // Counts spanning solo, pair, and full swarms; hashes include negative values (BlockPos
        // hashes routinely are); rotations exercise cosR/sinR.
        int[] counts = {1, 2, 3, 6, 12, 25};
        long[] hashes = {12345L, -987654321L, 0L};
        float[] rotations = {0f, 90f, 137.5f};
        int idx = 0;
        for (int count : counts) {
            long hash = hashes[idx % hashes.length];
            float rot = rotations[idx % rotations.length];
            list.add(new Scenario("n=" + count + " hash=" + hash + " rot=" + rot,
                    count, hash, rot, 3, 0.35f, 0.3f, 20f, 2_000, 42L + idx));
            idx++;
        }
        // One long soak at the handoff's ≥10k ticks.
        list.add(new Scenario("soak n=12 10k ticks", 12, -1122334455L, 45f, 3, 0.3f, 0.25f, 15f, 10_000, 7L));
        // Degenerate swarm params: single depth layer, zero y-range, zero jitter.
        list.add(new Scenario("flat n=8", 8, 555L, 0f, 1, 0.2f, 0f, 0f, 2_000, 99L));
        return list;
    }

    @TestFactory
    List<DynamicTest> bitwiseParityAcrossMatrix() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Scenario sc : scenarios()) {
            tests.add(DynamicTest.dynamicTest(sc.name(), () -> runParity(sc)));
        }
        return tests;
    }

    private static void runParity(Scenario sc) {
        FishSpec[] specs = sc.specs();

        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, sc.blockPosHash(), sc.rotation(),
                sc.depthLayers(), sc.xzSpread(), sc.yRange(), sc.rotationJitter());

        LegacyStepReference legacy = new LegacyStepReference();
        float[] lengths = new float[specs.length];
        boolean[] canSwim = new boolean[specs.length];
        boolean[] mirrored = new boolean[specs.length];
        for (int i = 0; i < specs.length; i++) {
            lengths[i] = specs[i].length();
            canSwim[i] = specs[i].canSwim();
            mirrored[i] = specs[i].mirrored();
        }
        legacy.rebuild(lengths, canSwim, mirrored, sc.blockPosHash(), sc.rotation(),
                sc.depthLayers(), sc.xzSpread(), sc.yRange(), sc.rotationJitter());

        // Rebuild parity: descriptors and initial state.
        assertEquals(legacy.count, engine.count(), "count");
        assertArrayEquals(legacy.seeds, engine.seeds, "seeds");
        assertArrayEquals(legacy.swimmers, engine.swimmers, "swimmers");
        assertArrayEquals(legacy.hoverMirrored, engine.hoverMirrored, "hoverMirrored");
        assertBits(legacy.scales, engine.lengths, "lengths", 0);
        assertBits(legacy.baseRotations, engine.baseRotations, "baseRotations", 0);

        // Trajectory parity, every tick.
        for (int tick = 1; tick <= sc.ticks(); tick++) {
            engine.step();
            legacy.step();
            assertBits(legacy.posL, engine.posL(), "posL", tick);
            assertBits(legacy.posY, engine.posY(), "posY", tick);
            assertBits(legacy.posD, engine.posD(), "posD", tick);
            assertBits(legacy.heading, engine.heading, "heading", tick);
            assertBits(legacy.speed, engine.speed, "speed", tick);
            assertBits(legacy.bank, engine.bank, "bank", tick);
        }

        // Interpolation parity at several alphas, including the depth sort.
        for (float alpha : new float[]{0f, 0.25f, 0.6f, 1f}) {
            engine.interpolate(alpha);
            legacy.interpolate(alpha);
            assertBits(legacy.renderX, engine.renderX, "renderX@" + alpha, sc.ticks());
            assertBits(legacy.renderY, engine.renderY, "renderY@" + alpha, sc.ticks());
            assertBits(legacy.renderZ, engine.renderZ, "renderZ@" + alpha, sc.ticks());
            assertArrayEquals(legacy.order, engine.order, "order@" + alpha);
        }

        // Animation coupling parity.
        for (int i = 0; i < engine.count(); i++) {
            assertEquals(Float.floatToRawIntBits(legacy.speedFactor(i)),
                    Float.floatToRawIntBits(engine.speedFactor(i)), "speedFactor[" + i + "]");
        }
    }

    private static void assertBits(float[] expected, float[] actual, String what, int tick) {
        for (int i = 0; i < expected.length; i++) {
            if (Float.floatToRawIntBits(expected[i]) != Float.floatToRawIntBits(actual[i])) {
                throw new AssertionError(what + "[" + i + "] diverged at tick " + tick
                        + ": legacy=" + expected[i] + " engine=" + actual[i]);
            }
        }
    }

    /** Rebuild-after-rebuild keeps ticking parity (simTick is not reset — matches legacy). */
    @Test
    void rebuildMidRunKeepsParity() {
        Scenario first = new Scenario("first", 6, 777L, 30f, 3, 0.35f, 0.3f, 20f, 500, 1L);
        Scenario second = new Scenario("second", 10, 777L, 30f, 3, 0.35f, 0.3f, 20f, 500, 2L);

        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        LegacyStepReference legacy = new LegacyStepReference();

        for (Scenario sc : List.of(first, second)) {
            FishSpec[] specs = sc.specs();
            float[] lengths = new float[specs.length];
            boolean[] canSwim = new boolean[specs.length];
            boolean[] mirrored = new boolean[specs.length];
            for (int i = 0; i < specs.length; i++) {
                lengths[i] = specs[i].length();
                canSwim[i] = specs[i].canSwim();
                mirrored[i] = specs[i].mirrored();
            }
            engine.rebuild(specs, sc.blockPosHash(), sc.rotation(),
                    sc.depthLayers(), sc.xzSpread(), sc.yRange(), sc.rotationJitter());
            legacy.rebuild(lengths, canSwim, mirrored, sc.blockPosHash(), sc.rotation(),
                    sc.depthLayers(), sc.xzSpread(), sc.yRange(), sc.rotationJitter());
            for (int tick = 1; tick <= sc.ticks(); tick++) {
                engine.step();
                legacy.step();
                assertBits(legacy.posL, engine.posL(), "posL(" + sc.name() + ")", tick);
                assertBits(legacy.posY, engine.posY(), "posY(" + sc.name() + ")", tick);
                assertBits(legacy.posD, engine.posD(), "posD(" + sc.name() + ")", tick);
            }
        }
    }
}
