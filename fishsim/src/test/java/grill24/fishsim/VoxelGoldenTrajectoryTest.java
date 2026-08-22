package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
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
 * Drift guard for the voxel-domain path (the multi-tank counterpart of
 * {@link GoldenTrajectoryTest} — same bootstrap protocol, L-shaped domain).
 */
class VoxelGoldenTrajectoryTest {

    private static final String FIXTURE_RESOURCE = "/golden/voxel-l-domain-trajectory.txt";
    private static final Path FIXTURE_SOURCE_PATH =
            Path.of("src", "test", "resources", "golden", "voxel-l-domain-trajectory.txt");

    private static final long SEED = 987654321L;
    private static final int TICKS = 4_000;
    private static final int SAMPLE_EVERY = 100;

    private static List<String> computeTrajectoryLines() {
        // Three species so the fixture also locks down the species-aware separation/schooling.
        FishSpec[] specs = {
                new FishSpec(0.10f, true, false, 0),
                new FishSpec(0.14f, true, true, 0),
                new FishSpec(0.08f, true, false, 0),
                new FishSpec(0.30f, true, true, 1),
                new FishSpec(0.12f, false, false, 1),
                new FishSpec(0.11f, true, true, 1),
                new FishSpec(0.09f, true, false, 0),
                new FishSpec(0.16f, true, false, 2),
                new FishSpec(0.07f, true, true, 2),
                new FishSpec(0.13f, true, false, 0),
                new FishSpec(0.20f, true, true, 1),
                new FishSpec(0.10f, true, false, 2),
        };
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, SEED, 30f, 20f, new VoxelDomain(VoxelDomainTest.lShape()));

        List<String> lines = new ArrayList<>();
        lines.add("# Golden voxel L-domain trajectory — raw float bits (hex) of posL/posY/posD per fish.");
        lines.add("# Scenario: n=12 seed=" + SEED + " ticks=" + TICKS + " sampled every " + SAMPLE_EVERY + ".");
        lines.add("# Regenerate ONLY deliberately (delete this file, run the suite twice).");
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
    void voxelTrajectoryMatchesGoldenFixture() throws IOException {
        List<String> actual = computeTrajectoryLines();

        try (InputStream in = VoxelGoldenTrajectoryTest.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            if (in == null) {
                Files.createDirectories(FIXTURE_SOURCE_PATH.getParent());
                Files.write(FIXTURE_SOURCE_PATH, actual, StandardCharsets.UTF_8);
                fail("Golden fixture was missing — recorded it to " + FIXTURE_SOURCE_PATH
                        + ". Rerun the suite and commit the file.");
            }
            List<String> expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            assertEquals(expected.size(), actual.size(), "fixture line count");
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i), actual.get(i), "fixture line " + (i + 1));
            }
        }
    }
}
