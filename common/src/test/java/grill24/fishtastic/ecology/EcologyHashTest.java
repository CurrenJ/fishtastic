package grill24.fishtastic.ecology;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ecology hash and seed derivation. The known values are load-bearing: changing the mixer,
 * the folding order, a salt or the seed derivation re-rolls every world's ecology, so a failure here
 * means "you changed saved-world-visible behaviour", not "update the number".
 */
class EcologyHashTest {

    // Captured from the first implementation; see the class doc before touching.
    private static final long PIN_A = 8491203612939376182L;
    private static final long PIN_B = -6206711726456645966L;
    private static final long PIN_C = -467748961220884301L;
    private static final long SEED_PIN = -3225294411586957294L;

    @Test
    void mixerMatchesTheSplitMix64ReferenceVector() {
        // SplitMix64 with state 0: the first output is mix(0 + golden gamma).
        assertEquals(0xE220A8397B1DCDAFL, EcologyHash.mix(0x9E3779B97F4A7C15L));
        assertEquals(0L, EcologyHash.mix(0L));
    }

    @Test
    void hashIsPinnedForKnownInputs() {
        assertEquals(PIN_A, EcologyHash.hash(0L, 0, 0, 0L, EcologyHash.SALT_VISIT_ACTIVITY));
        assertEquals(PIN_B, EcologyHash.hash(123456789L, -17, 42, 9001L, EcologyHash.SALT_RANK));
        assertEquals(PIN_C, EcologyHash.hash(-1L, Integer.MIN_VALUE, Integer.MAX_VALUE, -5L, EcologyHash.SALT_AMBIENT_SLOTS));
    }

    @Test
    void hashIsDeterministicAndOrderSensitive() {
        long a = EcologyHash.hash(7L, 3, 4, 5L, EcologyHash.SALT_START_CELL);
        assertEquals(a, EcologyHash.hash(7L, 3, 4, 5L, EcologyHash.SALT_START_CELL));
        assertNotEquals(a, EcologyHash.hash(7L, 4, 3, 5L, EcologyHash.SALT_START_CELL), "x and z must not commute");
        assertNotEquals(a, EcologyHash.hash(7L, 3, 4, 6L, EcologyHash.SALT_START_CELL));
        assertNotEquals(a, EcologyHash.hash(8L, 3, 4, 5L, EcologyHash.SALT_START_CELL));
    }

    @Test
    void saltsGiveIndependentStreams() {
        long[] salts = {EcologyHash.SALT_VISIT_ACTIVITY, EcologyHash.SALT_START_CELL, EcologyHash.SALT_RANK,
                EcologyHash.SALT_MEMBERS, EcologyHash.SALT_AMBIENT_SLOTS, EcologyHash.SALT_PHASE};
        Set<Long> seen = new HashSet<>();
        for (long salt : salts) {
            assertTrue(seen.add(EcologyHash.hash(99L, 1, 2, 3L, salt)));
        }
    }

    @Test
    void unitDoubleIsInRangeAndRoughlyUniform() {
        int buckets = 16;
        int[] counts = new int[buckets];
        int n = 160_000;
        long seed = 0xC0FFEEL;
        for (int i = 0; i < n; i++) {
            double u = EcologyHash.toUnitDouble(EcologyHash.hash(seed, i % 400, i / 400, 0L, EcologyHash.SALT_VISIT_ACTIVITY));
            assertTrue(u >= 0.0 && u < 1.0);
            counts[(int) (u * buckets)]++;
        }
        double expected = n / (double) buckets;
        for (int count : counts) {
            // 10000 expected, sigma ~97: 5% is >5 sigma
            assertTrue(Math.abs(count - expected) < expected * 0.05, "bucket " + count + " vs " + expected);
        }
        assertEquals(0.0, EcologyHash.toUnitDouble(0L));
        assertTrue(EcologyHash.toUnitDouble(-1L) < 1.0);
    }

    @Test
    void boundedIntStaysInRangeAndCoversIt() {
        int bound = 7;
        int[] counts = new int[bound];
        for (int i = 0; i < 70_000; i++) {
            int v = EcologyHash.toBoundedInt(EcologyHash.mix(i), bound);
            assertTrue(v >= 0 && v < bound);
            counts[v]++;
        }
        for (int count : counts) {
            assertTrue(Math.abs(count - 10_000) < 500, "count " + count);
        }
        assertEquals(0, EcologyHash.toBoundedInt(-1L, 1));
        assertThrows(IllegalArgumentException.class, () -> EcologyHash.toBoundedInt(5L, 0));
    }

    @Test
    void neighbouringChunksDoNotActivateTogether() {
        // Hash quality: adjacent chunks' activity bits must be uncorrelated.
        long seed = 31337L;
        int both = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            boolean a = EcologyHash.toUnitDouble(VisitWindows.activityHash(seed, i, 0, 0L)) < 0.1;
            boolean b = EcologyHash.toUnitDouble(VisitWindows.activityHash(seed, i + 1, 0, 0L)) < 0.1;
            if (a && b) both++;
        }
        // independent p=0.1 each: P(both)=0.01 -> 2000 expected (sigma ~44)
        assertTrue(Math.abs(both - 2000) < 300, "both=" + both);
    }

    @Test
    void ecologySeedIsStableAndDependsOnWorldSeedAndDimension() {
        long overworld = EcologySeed.derive(42L, "minecraft:overworld");
        assertEquals(SEED_PIN, overworld);
        assertEquals(overworld, EcologySeed.derive(42L, "minecraft:overworld"));
        assertNotEquals(overworld, EcologySeed.derive(42L, "minecraft:the_nether"));
        assertNotEquals(overworld, EcologySeed.derive(43L, "minecraft:overworld"));
        assertEquals(8, EcologySeed.fingerprint(overworld).length());
        assertNotEquals(Long.toHexString(overworld), EcologySeed.fingerprint(overworld));
    }
}
