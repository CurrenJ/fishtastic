package grill24.fishtastic.client.ambient;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ambient population's random stream (Fish in World, Phase 5). The load-bearing properties
 * are determinism ("fixed seed, fixed sequence") and snapshot/restore ("a retained state resumes
 * the stream where it stopped") — the latter is what the per-patch retention policy is built on,
 * so it must not be an accident of the implementation.
 */
class AmbientRandomTest {

    @Test
    void theSameSeedGivesTheSameSequence() {
        AmbientRandom a = new AmbientRandom(20261013L);
        AmbientRandom b = new AmbientRandom(20261013L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextLong(), b.nextLong(), "draw " + i);
        }
    }

    @Test
    void differentSeedsDiverge() {
        AmbientRandom a = new AmbientRandom(1L);
        AmbientRandom b = new AmbientRandom(2L);
        int equal = 0;
        for (int i = 0; i < 64; i++) {
            if (a.nextLong() == b.nextLong()) equal++;
        }
        assertTrue(equal <= 1, "two seeds must not produce the same sequence (coincidences=" + equal + ")");
    }

    /**
     * The retention contract: a state captured mid-stream and restored later continues the exact
     * sequence the original stream would have produced. This is "re-entering a patch resumes its
     * random state" with everything but the world removed.
     */
    @Test
    void aRestoredStateResumesTheSameSequence() {
        AmbientRandom original = new AmbientRandom(0x5EEDL);
        for (int i = 0; i < 100; i++) original.nextLong();
        long retained = original.state();

        long[] expected = new long[50];
        for (int i = 0; i < expected.length; i++) expected[i] = original.nextLong();

        AmbientRandom resumed = new AmbientRandom(-1L);
        resumed.restore(retained);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], resumed.nextLong(),
                    "a restored stream must not re-roll — draw " + i);
        }
    }

    @Test
    void restoringAnEarlierStateReplaysTheDrawsSince() {
        AmbientRandom stream = new AmbientRandom(7L);
        long before = stream.state();
        long first = stream.nextLong();
        stream.nextLong();
        stream.restore(before);
        assertEquals(first, stream.nextLong());
    }

    @Test
    void nextIntStaysInsideItsBoundAndCoversIt() {
        AmbientRandom random = new AmbientRandom(42L);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            int value = random.nextInt(7);
            assertTrue(value >= 0 && value < 7, "value " + value);
            seen.add(value);
        }
        assertEquals(7, seen.size(), "every residue of a small bound must be reachable");
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(-3));
    }

    @Test
    void nextDoubleAndFloatAreUnitInterval() {
        AmbientRandom random = new AmbientRandom(3L);
        double min = 1, max = 0;
        for (int i = 0; i < 10000; i++) {
            double d = random.nextDouble();
            float f = random.nextFloat();
            assertTrue(d >= 0 && d < 1, "double " + d);
            assertTrue(f >= 0 && f < 1, "float " + f);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        assertTrue(max - min > 0.9, "doubles must span the interval (span " + (max - min) + ")");
    }

    /** Reading the state must not advance it — a snapshot taken while idling stays valid. */
    @Test
    void readingTheStateDoesNotConsumeTheStream() {
        AmbientRandom random = new AmbientRandom(11L);
        long a = random.state();
        long b = random.state();
        assertEquals(a, b);
        assertNotEquals(a, new AmbientRandom(a).nextLong(), "state is not the next output");
    }
}
