package grill24.fishtastic.ecology;

import java.util.ArrayList;
import java.util.List;

/**
 * The seeded ambient roster of one chunk (world fish exploration E6, E12, E13). Pure: no level, no
 * registry, no random number generator. Every number is a function of {@code (seed, chunk, slot,
 * lifespan index)} through {@link EcologyHash}, so two clients holding the same seed, the same
 * water and the same game time derive the same fish with the same IDs, however they got there.
 *
 * <h2>Slots</h2>
 * A chunk with usable fluid has {@link #slotCount} slots, scaled by its usable volume. A slot is a
 * sequence of lifespans, never a stored fish.
 *
 * <h2>Lifespans</h2>
 * The chunk has a period {@code P} (hashed, between {@link Params#minPeriodTicks} and
 * {@link Params#maxPeriodTicks}) and slot {@code s} a phase {@code t0_s} in it, spread evenly by a
 * golden-ratio sequence. Lifespan {@code k} sits in the nominal
 * interval {@code [t0 + kP, t0 + (k+1)P)}; the fish arrives {@code a_k} ticks into it and leaves
 * {@code d_k} ticks before its end, with {@code a_k, d_k} hashed in {@code [0, P / jitterDivisor)}.
 * Between a departure and the next arrival the slot is vacant. Because the slots' phases are
 * spread evenly around the period, arrivals and departures of different slots are scattered across
 * time (E13): at any moment only a few fish are coming or going, and a condition change never swaps a chunk's
 * population at once.
 *
 * <h2>Identity</h2>
 * {@code fishId = hash(seed, chunk, slot, lifespan index)}; everything else about the fish (its
 * home cell, its species draw, its density rank) is a further hash of that ID, so it follows the
 * individual and not the slot.
 */
public final class AmbientRoster {

    /**
     * @param maxSlotsPerChunk  slots of a chunk whose usable volume is full
     * @param cellsPerSlot      usable fluid cells that earn one slot
     * @param minCells          below this a chunk holds only a puddle and gets no slots
     * @param minPeriodTicks    shortest slot period
     * @param maxPeriodTicks    longest slot period
     * @param jitterDivisor     arrival and departure each fall within {@code period / jitterDivisor}
     */
    public record Params(int maxSlotsPerChunk, int cellsPerSlot, int minCells, int minPeriodTicks,
                         int maxPeriodTicks, int jitterDivisor) {
        public Params {
            if (maxSlotsPerChunk <= 0 || cellsPerSlot <= 0 || minCells < 0) {
                throw new IllegalArgumentException("slot sizing must be positive");
            }
            if (minPeriodTicks <= 0 || maxPeriodTicks < minPeriodTicks || jitterDivisor < 2) {
                throw new IllegalArgumentException("bad lifespan model");
            }
        }
    }

    /**
     * A full chunk of 4-deep fluid (about 1000 cells) gets 16 slots; each slot period is 10 to 20
     * minutes (12000 to 24000 ticks), and the fish is absent for about 4% of it (arrival and departure each jitter within a 24th of the period).
     */
    public static final Params DEFAULT = new Params(16, 64, 8, 12000, 24000, 24);

    private static final long SALT_PERIOD = 0x0A11_0001L;
    private static final long SALT_PHASE = 0x0A11_0002L;
    private static final long SALT_ARRIVE = 0x0A11_0003L;
    private static final long SALT_DEPART = 0x0A11_0004L;
    private static final long SALT_CELL = 0x0A11_0005L;
    private static final long SALT_HEIGHT = 0x0A11_0006L;
    private static final long SALT_SPECIES = 0x0A11_0007L;
    private static final long SALT_RANK = 0x0A11_0008L;
    private static final long SALT_MIRROR = 0x0A11_0009L;
    private static final long GAMMA = 0x9E3779B97F4A7C15L;
    private static final double GOLDEN = 0.6180339887498949;

    private AmbientRoster() {
    }

    /** One fish's presence: its identity and when it arrives and leaves (departure is exclusive). */
    public record Lifespan(long fishId, int chunkX, int chunkZ, int slot, long index, long arrivalTick,
                           long departureTick) {
        public boolean presentAt(long time) {
            return time >= arrivalTick && time < departureTick;
        }

        /** 0 at arrival, 1 at departure. */
        public double phaseAt(long time) {
            return departureTick <= arrivalTick ? 1.0
                    : Math.max(0.0, Math.min(1.0, (time - arrivalTick) / (double) (departureTick - arrivalTick)));
        }
    }

    /** Slots earned by {@code usableCells} of fluid: none for a puddle, else scaled and capped. */
    public static int slotCount(int usableCells, Params params) {
        if (usableCells < params.minCells()) return 0;
        long slots = (usableCells + params.cellsPerSlot() - 1L) / params.cellsPerSlot();
        return (int) Math.max(1, Math.min(params.maxSlotsPerChunk(), slots));
    }

    private static long slotBase(long seed, int chunkX, int chunkZ, int slot) {
        return EcologyHash.hash(seed, chunkX, chunkZ, slot, EcologyHash.SALT_AMBIENT_SLOTS);
    }

    private static long sub(long base, long salt, long k) {
        return EcologyHash.mix(EcologyHash.mix(base ^ salt) + GAMMA + k);
    }

    /**
     * The chunk's lifespan period, hashed per chunk between {@link Params#minPeriodTicks} and
     * {@link Params#maxPeriodTicks}. Shared by all its slots so their evenly spread phases stay
     * evenly spread; the variety between fish comes from IDs, species and the per-lifespan jitter.
     */
    public static long period(long seed, int chunkX, int chunkZ, Params params) {
        long base = EcologyHash.hash(seed, chunkX, chunkZ, -1L, EcologyHash.SALT_AMBIENT_SLOTS);
        int span = params.maxPeriodTicks() - params.minPeriodTicks() + 1;
        return params.minPeriodTicks() + EcologyHash.toBoundedInt(sub(base, SALT_PERIOD, 0), span);
    }

    /**
     * The slot's phase in {@code [0, period)}: a golden-ratio sequence from a hashed per-chunk start,
     * so any number of slots 0..N-1 is spread nearly evenly around the period (low discrepancy), which
     * is what keeps arrivals and departures of a chunk's fish from bunching (E13).
     */
    public static long phase(long seed, int chunkX, int chunkZ, int slot, Params params) {
        long base = EcologyHash.hash(seed, chunkX, chunkZ, -1L, EcologyHash.SALT_AMBIENT_SLOTS);
        double start = EcologyHash.toUnitDouble(sub(base, SALT_PHASE, 0));
        double fraction = (start + slot * GOLDEN) % 1.0;
        return (long) (fraction * period(seed, chunkX, chunkZ, params));
    }

    /** {@code hash(seed, chunk, slot, lifespan index)}. */
    public static long fishId(long seed, int chunkX, int chunkZ, int slot, long index) {
        return EcologyHash.mix(slotBase(seed, chunkX, chunkZ, slot) + GAMMA + index);
    }

    /** The lifespan {@code index} of a slot, whether or not it is the current one. */
    public static Lifespan lifespan(long seed, int chunkX, int chunkZ, int slot, long index, Params params) {
        long period = period(seed, chunkX, chunkZ, params);
        long nominal = phase(seed, chunkX, chunkZ, slot, params) + index * period;
        long base = slotBase(seed, chunkX, chunkZ, slot);
        long window = Math.max(1, period / params.jitterDivisor());
        long arrive = nominal + EcologyHash.toBoundedInt(sub(base, SALT_ARRIVE, index), (int) window);
        long depart = nominal + period - EcologyHash.toBoundedInt(sub(base, SALT_DEPART, index), (int) window);
        return new Lifespan(fishId(seed, chunkX, chunkZ, slot, index), chunkX, chunkZ, slot, index, arrive, depart);
    }

    /** The slot's fish present at {@code time}, or null while the slot is vacant. */
    public static Lifespan lifespanAt(long seed, int chunkX, int chunkZ, int slot, long time, Params params) {
        long period = period(seed, chunkX, chunkZ, params);
        long index = Math.floorDiv(time - phase(seed, chunkX, chunkZ, slot, params), period);
        Lifespan lifespan = lifespan(seed, chunkX, chunkZ, slot, index, params);
        return lifespan.presentAt(time) ? lifespan : null;
    }

    /** Every fish present in the chunk at {@code time}, in slot order. */
    public static List<Lifespan> present(long seed, int chunkX, int chunkZ, int slots, long time, Params params) {
        List<Lifespan> out = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            Lifespan lifespan = lifespanAt(seed, chunkX, chunkZ, slot, time, params);
            if (lifespan != null) out.add(lifespan);
        }
        return out;
    }

    // ------------------------------------------------------------------ per-fish values

    /** Density rank in [0, 1): a fish is drawn when its rank is under {@code density / 100}. */
    public static double rank(long fishId) {
        return EcologyHash.toUnitDouble(sub(fishId, SALT_RANK, 0));
    }

    /**
     * Whether the density setting draws this fish. The subset is stable: raising the density only
     * adds fish, lowering it only removes them, and it never changes which fish exist.
     */
    public static boolean drawn(long fishId, int densityPercent) {
        if (densityPercent <= 0) return false;
        if (densityPercent >= 100) return true;
        return rank(fishId) < densityPercent / 100.0;
    }

    /** Index into the chunk's fluid columns (in their fixed order) of the fish's home cell. */
    public static int homeColumn(long fishId, int columnCount) {
        return EcologyHash.toBoundedInt(sub(fishId, SALT_CELL, 0), Math.max(1, columnCount));
    }

    /** Height inside the home column's usable span, in [0, 1) (0 = floor of the span). */
    public static double heightFraction(long fishId) {
        return EcologyHash.toUnitDouble(sub(fishId, SALT_HEIGHT, 0));
    }

    /** The uniform draw that picks the species from the pool at the fish's arrival. */
    public static double speciesUnit(long fishId) {
        return EcologyHash.toUnitDouble(sub(fishId, SALT_SPECIES, 0));
    }

    public static boolean mirrored(long fishId) {
        return (sub(fishId, SALT_MIRROR, 0) & 1L) != 0L;
    }
}
