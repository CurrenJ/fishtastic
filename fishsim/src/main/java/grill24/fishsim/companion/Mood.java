package grill24.fishsim.companion;

/**
 * How the ambient world makes one fish feel (docs/fish-companions.md Phase 5, spec §6),
 * read against its own species' {@link Habitat}: cold, glum, glad of the rain, sleepy in its
 * off-hours, moonstruck. Each feeling eases in and out over seconds and acts only as a small,
 * capped modifier on the drives and body the temperament already sets (the owner's call: subtle,
 * so seen % and fades don't drop). It never adds a behaviour of its own, except where a reaction
 * needs one: a shivering fish goes to bask by a warm thing (an inspection), a glad one does a lap
 * now and then, a moonstruck one idles high with its nose up.
 *
 * <p>With {@link Ambient#NEUTRAL} or {@link Habitat#NONE} every feeling stays at exactly 0 and every
 * lever at exactly its neutral value, so a run without an ambient is bitwise what it was.
 */
final class Mood {

    private static final double DT = 1.0 / 20;
    /** Seconds for a feeling to come on fully, and to wear off. */
    static final double EASE_IN_SECONDS = 6, EASE_OUT_SECONDS = 4;

    /** Biome temperatures (Minecraft's scale): below COLD_BELOW it snows; HOT_FROM and up is desert, badlands, savanna, the Nether. */
    static final float COLD_BELOW = 0.15f, HOT_FROM = 1.5f, WARM_FROM = 1.0f;
    /** The owner this close to a warm thing (blocks from their chest) keeps the fish warm. */
    static final double WARM_NEAR = 2.5;
    /** The fish this close to a warm thing is basking by it (just past its inspection hold). */
    static final double BASK_NEAR = 2.0;
    /** Seconds a bask lasts, and how long the warmth stays with it afterwards before the cold creeps back. */
    static final double BASK_SECONDS = 8, WARM_HOLD_SECONDS = 20;
    /** A feeling above this shows in the pose. */
    static final float SHOWS = 0.5f;

    /** 0..1, eased: shivering with cold. */
    float cold;
    /** 0..1, eased: miserable (too hot, out of the heat, or rain it dislikes). */
    float glum;
    /** 0..1, eased: glad of the rain (or a Nether fish by a fire). */
    float glee;
    /** Water joy is a visible tone, without hidden movement multipliers. */
    float waterJoy;
    /** 0..1, eased: its off-hours. */
    float sleepy;
    /** 0..1, eased: a full moon at night, for a species that comes out under one. */
    float moon;
    /** Warm: its owner is by a warm thing, it's basking, or it basked a short while ago. */
    boolean warmed;
    /** Off to bask by a warm thing: cold (or a Nether fish out of the heat) and feeling it, until it has basked. */
    boolean wantsWarmth;
    private int warmHold, baskTicks;

    // The levers, at exactly their neutral values while every feeling is 0.
    float pace = 1, rest = 1, dwell = 1, jitter = 1, band = 1, curiosity = 1, stillness = 0;
    /** Extra height for idle spots, blocks (the moon idle). */
    float lift = 0;

    // Targets, for the overlay.
    float coldTo, glumTo, gleeTo, sleepyTo, moonTo;

    /**
     * @param chest the owner's chest
     * @param self  where the fish is (a trio's leader for the trio)
     */
    void update(Ambient a, Habitat h, Vec chest, Vec self) {
        targets(a, h, chest, self);
        cold = ease(cold, coldTo);
        glum = ease(glum, glumTo);
        glee = ease(glee, gleeTo);
        sleepy = ease(sleepy, sleepyTo);
        moon = ease(moon, moonTo);
        levers();
    }

    boolean any() {
        return cold > 0 || glum > 0 || glee > 0 || sleepy > 0 || moon > 0;
    }

    boolean shivering() {
        return cold > SHOWS;
    }

    boolean moonstruck() {
        return moon > SHOWS;
    }

    private void targets(Ambient a, Habitat h, Vec chest, Vec self) {
        coldTo = glumTo = gleeTo = sleepyTo = moonTo = 0;
        if (h.equals(Habitat.NONE)) {
            warmed = wantsWarmth = false;
            return;
        }
        boolean home = h.atHome(a);
        float temp = a.temperature();
        Ambient.Weather w = a.weather();
        boolean hot = h.climate() == Habitat.Climate.HOT;
        boolean ownerWarm = false, basking = false;
        for (Vec p : a.warmth()) {
            if (p.distanceTo(chest) <= WARM_NEAR) ownerWarm = true;
            if (p.distanceTo(self) <= BASK_NEAR) basking = true;
        }
        // The bask's time counts over the whole trip: a drifter bobbing in and out of reach still fills up.
        if (!wantsWarmth) baskTicks = 0;
        else if (basking) baskTicks++;
        if (ownerWarm || basking) warmHold = (int) (WARM_HOLD_SECONDS * 20);
        else if (warmHold > 0) warmHold--;
        warmed = warmHold > 0;

        // Comfort: the climate of its homes against the biome it's in. At home, nothing grates.
        if (!home && !Float.isNaN(temp)) {
            boolean snowy = temp < COLD_BELOW || w == Ambient.Weather.SNOW;
            switch (h.climate()) {
                case TEMPERATE -> {
                    if (snowy) coldTo = w == Ambient.Weather.SNOW ? 1f : 0.8f;
                    else if (temp >= HOT_FROM) glumTo = 0.6f;
                }
                case COLD -> {
                    if (temp >= WARM_FROM) glumTo = 0.6f;
                }
                case HOT -> {
                    if (snowy) coldTo = 1f;
                    else if (temp < WARM_FROM) glumTo = 0.5f;
                }
            }
        }
        // It sets off once the cold shows, and stays by the fire until it has basked its fill.
        boolean chilly = coldTo > 0 || (hot && glumTo > 0);
        boolean feelsIt = cold > SHOWS || (hot && glum > SHOWS);
        if (!chilly || ownerWarm || baskTicks >= BASK_SECONDS * 20) wantsWarmth = false;
        else if (feelsIt) wantsWarmth = true;
        if (warmed) {
            // By a fire the cold goes; a Nether fish out of the heat cheers up there.
            coldTo = 0;
            if (hot && glumTo > 0) {
                glumTo = 0;
                gleeTo = 0.5f;
            }
        }

        // Weather: rain gladdens most fish; one whose profile dislikes it, or a Nether fish, sulks.
        // A cold-climate fish takes snow as rain.
        Ambient.Weather wet = w == Ambient.Weather.SNOW && h.climate() == Habitat.Climate.COLD ? Ambient.Weather.RAIN : w;
        if (wet == Ambient.Weather.RAIN || wet == Ambient.Weather.THUNDER) {
            float like = h.weather(w);
            if (hot || like < 1f) glumTo = Math.max(glumTo, 0.5f);
            else if (coldTo == 0) gleeTo = Math.max(gleeTo, like > 1f ? 1f : 0.7f);
        }

        // Time and moon: sleepy in its off-hours; a full-moon species idles high at night under one.
        Ambient.DayPart part = a.dayPart();
        if (part != null) {
            boolean moonNight = part == Ambient.DayPart.NIGHT && a.weather() == Ambient.Weather.CLEAR && !a.place().contains("sky:blocked") && a.fullMoon() && h.fullMoon() > 1f;
            if (moonNight) moonTo = 1f;
            else if (h.hours(part) < 1f || (h.hours(part) <= 1f && h.hours(opposite(part)) > 1f)) sleepyTo = 1f;
        }
    }

    static Ambient.DayPart opposite(Ambient.DayPart p) {
        return switch (p) {
            case DAY -> Ambient.DayPart.NIGHT;
            case NIGHT -> Ambient.DayPart.DAY;
            case DAWN -> Ambient.DayPart.DUSK;
            case DUSK -> Ambient.DayPart.DAWN;
        };
    }

    private static float ease(float v, float to) {
        if (v == to) return v;
        if (v < to) return (float) Math.min(to, v + DT / EASE_IN_SECONDS);
        return (float) Math.max(to, v - DT / EASE_OUT_SECONDS);
    }

    /**
     * The levers, each capped (plan §5 Phase 5: subtle). Cold huddles in, slows and shakes; glum
     * slows, lingers and loses interest; glee quickens, ranges a little wider and rests less;
     * sleepy slows, lingers, hangs stiller and rests sooner.
     */
    private void levers() {
        if (!any()) {
            pace = rest = dwell = jitter = band = curiosity = 1;
            stillness = lift = 0;
            return;
        }
        float c = cold, g = glum, j = glee, s = sleepy;
        pace = (1 - 0.12f * c) * (1 - 0.15f * g) * (1 + 0.12f * j) * (1 - 0.2f * s);
        rest = (1 + 1.0f * s) * (1 - 0.4f * j);
        dwell = 1 - 0.2f * j;
        jitter = (1 + 0.4f * c) * (1 + 0.15f * j);
        band = 1 + 0.1f * j;
        curiosity = 1 + 0.2f * j;
        stillness = 0.15f * g + 0.25f * s;
        lift = 0.5f * moon - 0.3f * glum;
    }

    /**
     * A group member's pose: the group mind's hint ({@code lead}) with the leader's mood hints taken
     * off and this member's own put on, so a mixed shoal can be split (owner, 2026-10-15). For the
     * leader itself it's its own hint unchanged.
     */
    static PoseHint memberPose(PoseHint lead, Mood own, boolean idleDwelling) {
        PoseHint base = lead == PoseHint.SHIVER || lead == PoseHint.MOON_GAZE || lead == PoseHint.DROOP || lead == PoseHint.GLEE ? PoseHint.NONE : lead;
        boolean plain = base == PoseHint.NONE || base == PoseHint.SLEEPY || base == PoseHint.TREMBLE;
        if (own.shivering() && plain) return PoseHint.SHIVER;
        if (own.moonstruck() && idleDwelling && (base == PoseHint.NONE || base == PoseHint.SLEEPY)) return PoseHint.MOON_GAZE;
        // A leader's mood hint stood in for the drowsy or trembling pose its mind chose; without
        // the mood, a member shows none rather than guess which.
        return base;
    }

    /** The overlay's line. */
    String describe() {
        return String.format(java.util.Locale.ROOT, "mood cold %.2f glum %.2f glee %.2f sleepy %.2f moon %.2f%s",
                cold, glum, glee, sleepy, moon, warmed ? " warmed" : wantsWarmth ? " wants-warmth" : "");
    }
}
