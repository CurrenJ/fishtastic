package grill24.fishsim.companion;

import java.util.List;
import java.util.Locale;

/**
 * How a species' temperament colours its companion (spec §4, docs/fish-companions-sim-plan.md
 * §3.3, §10.5): weights on the drives and the timing of decisions, never a different set of
 * behaviours — temperament changes <i>how</i> a display mode behaves, not <i>which</i> one is used.
 *
 * <p>{@link #BASELINE} is the behaviour the owner approved in Phase 1: every weight 1, nothing
 * special switched on. Each real temperament is a set of departures from it.
 *
 * @param name           the temperament's id (its JSON file name), for overlays
 * @param curiosity      gain on curiosity toward things that appear
 * @param inspectSpacing scale on how close it comes to things it inspects (bigger keeps further off)
 * @param wariness       gain on wariness toward hostiles, cats and predator fish
 * @param rest           gain on how fast rest builds while the owner idles
 * @param band           scale on the comfort band and riding distance (smaller stays tighter)
 * @param dwell          scale on how long it lingers at a spot
 * @param decisions      scale on the time between changes of mind (where it rides, where it lingers)
 * @param regular        0 = intervals drawn from their whole range, 1 = always the middle (rhythmic)
 * @param commitment     scale on the minimum time a goal is kept (never under a second)
 * @param jitter         scale on heading noise
 * @param pace           scale on its cruising speed
 * @param stillness      0..1: how still it hangs while lingering (1 = motionless)
 * @param guard          faces hostiles from in front of its owner, puffed up, instead of hiding;
 *                       bites the air when its owner is hurt (aggressive)
 * @param raceAhead      dashes out ahead while its owner sprints (sprinter)
 * @param ambush         stalks dropped items: creeps up, holds still, then darts (ambusher)
 * @param nuzzle         nuzzles its owner while they stand still (docile)
 * @param peekOut        peeks out from cover at what scared it (skittish)
 * @param tremble        an idle shiver for the animator (trembler)
 */
public record Temperament(String name, float curiosity, float inspectSpacing, float wariness, float rest,
                          float band, float dwell, float decisions, float regular, float commitment, float jitter,
                          float pace, float stillness, boolean guard, boolean raceAhead, boolean ambush, boolean nuzzle,
                          boolean peekOut, boolean tremble) {

    public static final Temperament BASELINE = new Temperament("baseline", 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0,
            false, false, false, false, false, false);

    /** Stays tighter; rests readily; nuzzles you while you stand still. */
    public static final Temperament DOCILE = new Temperament("docile", 1, 1, 0.8f, 1.5f, 0.8f, 1.3f, 1, 0, 1, 1, 1, 0.2f,
            false, false, false, true, false, false);
    /** Quick to hide, slow to come close, peeks out from cover. */
    public static final Temperament SKITTISH = new Temperament("skittish", 0.7f, 1.6f, 2.0f, 1, 1, 1, 1, 0, 1, 1.1f, 1, 0,
            false, false, false, false, true, false);
    /** Puffs up at hostiles from in front of its owner; bites the air when they're hurt. */
    public static final Temperament AGGRESSIVE = new Temperament("aggressive", 1.1f, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0,
            true, false, false, false, false, false);
    /** Dashes ahead while its owner sprints; quicker, less restful. */
    public static final Temperament SPRINTER = new Temperament("sprinter", 1, 1, 1, 0.6f, 1, 1, 1, 0, 1, 1, 1.2f, 0,
            false, true, false, false, false, false);
    /** Hangs still; stalks and darts at dropped items. */
    public static final Temperament AMBUSHER = new Temperament("ambusher", 1.3f, 1, 1, 1, 1, 1.5f, 1, 0, 1, 0.7f, 1, 0.6f,
            false, false, true, false, false, false);
    /** Takes the active role in pet-to-pet scenes (Phase 4); otherwise a touch more curious. */
    public static final Temperament WILY = new Temperament("wily", 1.2f, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0,
            false, false, false, false, false, false);
    /** Timing only: long, still hovers. */
    public static final Temperament GHOST = new Temperament("ghost", 1, 1, 1, 2.0f, 1, 2.0f, 1.3f, 0, 1, 0.6f, 0.8f, 0.85f,
            false, false, false, false, false, false);
    /** Timing only: jittery — quick changes of mind, a wandering heading. */
    public static final Temperament ERRATIC = new Temperament("erratic", 1, 1, 1, 1, 1, 0.6f, 0.5f, 0, 0.7f, 1.5f, 1, 0,
            false, false, false, false, false, false);
    /** Timing only: shaky — a shiver at rest, a slightly unsteady heading. */
    public static final Temperament TREMBLER = new Temperament("trembler", 1, 1, 1, 1, 1, 1, 1, 0, 1, 1.25f, 1, 0,
            false, false, false, false, false, true);
    /** Timing only: rhythmic — every interval the same length. */
    public static final Temperament METRONOME = new Temperament("metronome", 1, 1, 1, 1, 1, 1, 1, 1, 1, 0.8f, 1, 0,
            false, false, false, false, false, false);

    /** Every temperament in the data pack, in a stable order. */
    public static final List<Temperament> ALL = List.of(DOCILE, SKITTISH, AGGRESSIVE, SPRINTER, AMBUSHER, WILY, GHOST,
            ERRATIC, TREMBLER, METRONOME);

    /** The temperament with this id (a temperament JSON's file name), or the baseline for unknown ids. */
    public static Temperament named(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (Temperament t : ALL) if (t.name.equals(key)) return t;
        return BASELINE;
    }

    /** A new temperament like this one with rest switched off (for measuring what rest does). */
    public Temperament withoutRest() {
        return new Temperament(name + "-norest", curiosity, inspectSpacing, wariness, 0, band, dwell, decisions, regular,
                commitment, jitter, pace, stillness, guard, raceAhead, ambush, nuzzle, peekOut, tremble);
    }
}
