package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The long-term drift guard (docs/fish-sim-engine-handoff.md Task 3): a committed fixture of the
 * exact trajectory bits the extracted engine produced while it was still bitwise-locked to the
 * legacy code by {@link ParityTest}. {@link LegacyStepReference} gets deleted after the adapter
 * rewire ships; this fixture stays forever — any later refactor that silently changes behavior
 * fails here even with the reference gone.
 *
 * <p>Bootstrap: when the fixture resource is missing, the test writes it to
 * {@code src/test/resources} and fails asking for a rerun (the second run then passes and the
 * file gets committed). It must only ever be regenerated deliberately, with parity green.
 */
class GoldenTrajectoryTest {

    private static final String FIXTURE_RESOURCE = "/golden/single-box-trajectory.txt";
    private static final Path FIXTURE_SOURCE_PATH =
            Path.of("src", "test", "resources", "golden", "single-box-trajectory.txt");

    // Fixture scenario: a full mixed swarm on the default tunables. Deliberately mirrors a real
    // in-game configuration (12 fish, jittered rotations, 3 depth layers).
    private static final long BLOCK_POS_HASH = -1122334455L;
    private static final float ROTATION = 45f;
    private static final int TICKS = 4_000;
    private static final int SAMPLE_EVERY = 100;

    private static FishSpec[] fixtureSpecs() {
        // Hand-picked, hard-coded population (no java.util.Random here — the fixture must not
        // depend on JDK RNG stream stability across releases for its *inputs*; the engine's own
        // seeded scatter is part of what the fixture locks down).
        return new FishSpec[]{
                new FishSpec(0.10f, true, false, 0),
                new FishSpec(0.14f, true, true, 0),
                new FishSpec(0.08f, true, false, 0),
                new FishSpec(0.30f, true, true, 0),   // fails the 2.5×length gate in a 0.7 run → hovers
                new FishSpec(0.12f, false, false, 0), // floor-anchored species
                new FishSpec(0.11f, true, true, 0),
                new FishSpec(0.09f, true, false, 0),
                new FishSpec(0.16f, true, false, 0),
                new FishSpec(0.07f, true, true, 0),
                new FishSpec(0.13f, true, false, 0),
                new FishSpec(0.20f, true, true, 0),
                new FishSpec(0.10f, true, false, 0),
        };
    }

    private static List<String> computeTrajectoryLines() {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(fixtureSpecs(), BLOCK_POS_HASH, ROTATION, 3, 0.35f, 0.3f, 20f);

        List<String> lines = new ArrayList<>();
        lines.add("# Golden single-box trajectory — raw float bits (hex) of posL/posY/posD per fish.");
        lines.add("# Scenario: n=12 hash=" + BLOCK_POS_HASH + " rot=" + ROTATION
                + " ticks=" + TICKS + " sampled every " + SAMPLE_EVERY + ".");
        lines.add("# Regenerate ONLY deliberately (delete this file, run the suite twice) with parity green.");
        appendSample(lines, engine, 0);
        for (int tick = 1; tick <= TICKS; tick++) {
            engine.step();
            if (tick % SAMPLE_EVERY == 0) appendSample(lines, engine, tick);
        }
        return lines;
    }

    private static void appendSample(List<String> lines, FlockEngine engine, int tick) {
        StringBuilder sb = new StringBuilder("t=").append(tick);
        for (int i = 0; i < engine.count(); i++) {
            sb.append(' ')
                    .append(Integer.toHexString(Float.floatToRawIntBits(engine.posL()[i]))).append(',')
                    .append(Integer.toHexString(Float.floatToRawIntBits(engine.posY()[i]))).append(',')
                    .append(Integer.toHexString(Float.floatToRawIntBits(engine.posD()[i])));
        }
        lines.add(sb.toString());
    }

    @Test
    void trajectoryMatchesGoldenFixture() throws IOException {
        List<String> actual = computeTrajectoryLines();

        try (InputStream in = GoldenTrajectoryTest.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            if (in == null) {
                Files.createDirectories(FIXTURE_SOURCE_PATH.getParent());
                Files.write(FIXTURE_SOURCE_PATH, actual, StandardCharsets.UTF_8);
                fail("Golden fixture was missing — recorded it to " + FIXTURE_SOURCE_PATH
                        + ". Verify parity is green, rerun the suite, and commit the file.");
            }
            List<String> expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            assertEquals(expected.size(), actual.size(), "fixture line count");
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i), actual.get(i), "fixture line " + (i + 1));
            }
        }
    }
}
