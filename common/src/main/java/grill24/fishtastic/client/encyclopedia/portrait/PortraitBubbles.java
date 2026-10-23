package grill24.fishtastic.client.encyclopedia.portrait;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure simulation of one Fishopedia portrait's ambient bubbles (embers in the Nether), a Java port of
 * {@code Plate} in docs/encyclopedia-redesign/README.md. Units are logical GUI px
 * and seconds; the plate is {@link #W} x {@link #H}. No Minecraft client calls, so it is unit-testable.
 * Deterministic for a (theme, seed) pair and the same sequence of calls.
 */
public final class PortraitBubbles {
    public static final int W = 156;
    public static final int H = 66;
    /** Fish slot x-range on the plate (a click inside it puts the plume in front of the fish). */
    public static final int FISH_SLOT_X = 46;
    public static final int FISH_SLOT_SIZE = 64;

    /** Prototype WARMUP: pre-simulated seconds so the plate is alive when the page opens. */
    public static final float WARMUP = 5.0f;
    public static final int MAX_BUBBLES = 32;
    public static final int MAX_WITH_EXTRAS = 56;
    public static final float CLICK_COOLDOWN = 0.35f;
    public static final float MAX_DT = 0.1f;
    public static final float POP_DURATION = 0.25f;
    /** Seconds per vanilla bubble_pop frame (5 frames over {@link #POP_DURATION}). */
    public static final float POP_FRAME = 0.05f;
    private static final int SURFACE_Y = 3;
    private static final float PHASE_RANGE = 6.3f;   // the prototype's phase range
    private static final float WARM_STEP = 1f / 20f;

    public enum Kind { MICRO, SMALL, MEDIUM, SNOW, EMBER, BLOB }

    /** One live particle. Mutated only by the owning simulation. */
    public static final class Bubble {
        public Kind kind;
        public float x, y, vx, vy, age, life, amp, freq, phase, alpha;
        public int size;
        public boolean front;
        /** 0xRRGGBB, for micro bubbles and snow. */
        public int colour = 0xFFFFFF;
        public boolean popping;
        public float popAge;

        /** Pixel-snapped draw position, wobble included, as the prototype's {@code _draw}. */
        public int drawX() {
            return Math.round(x + wobble());
        }

        public int drawY() {
            return Math.round(y);
        }

        public float wobble() {
            return (float) (amp * Math.sin(2.0 * Math.PI * freq * age + phase));
        }

        /** Index 0..4 of the vanilla bubble_pop frame while popping. */
        public int popFrame() {
            return Math.min(4, (int) (popAge / POP_FRAME));
        }
    }

    private final PortraitTheme theme;
    private final Random rng;
    private final List<Bubble> bubbles = new ArrayList<>();
    private final float[] acc = new float[Kind.values().length];
    private float plumeTimer;
    private float time;
    private float lastClick = -9f;
    private int peak;
    private boolean paused;
    private boolean reducedEffects;
    private boolean muted;

    public PortraitBubbles(PortraitTheme theme, long seed) {
        this.theme = theme;
        this.rng = new Random(seed);
        this.plumeTimer = uniform(1.5f, 4.0f);
    }

    // ------------------------------------------------------------------ configuration

    public PortraitTheme theme() { return theme; }

    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean paused() { return paused; }

    /** The existing reducedCelebrationEffects accessibility option: skips the claim burst. */
    public void setReducedEffects(boolean reduced) { this.reducedEffects = reduced; }
    public boolean reducedEffects() { return reducedEffects; }

    /** Uncaught species: drawn at 50% opacity. */
    public void setMuted(boolean muted) { this.muted = muted; }
    public boolean muted() { return muted; }

    /** Opacity multiplier the renderer applies to every particle. */
    public float opacity() { return muted ? 0.5f : 1.0f; }

    public List<Bubble> bubbles() { return Collections.unmodifiableList(bubbles); }
    public int alive() { return bubbles.size(); }
    /** Highest alive count since construction or the last {@link #resetPeak()}/{@link #warmStart(float)}. */
    public int peak() { return peak; }
    public void resetPeak() { peak = bubbles.size(); }
    public float time() { return time; }
    /** Seconds until the next ambient plume fires (test hook). */
    float plumeTimer() { return plumeTimer; }

    // ------------------------------------------------------------------ lifecycle

    /** Pre-simulates {@code seconds} at 20 Hz regardless of the pause state, then resets the peak. */
    public void warmStart(float seconds) {
        int steps = Math.round(seconds / WARM_STEP);
        for (int i = 0; i < steps; i++) stepInternal(WARM_STEP, true);
        resetPeak();
    }

    /** Page just opened (after the warm start): schedules the welcome plume in 0.8-1.6 s. */
    public void onOpen() {
        plumeTimer = uniform(0.8f, 1.6f);
    }

    /** Advances by a real frame time, clamped to 0.1 s; does nothing while paused. */
    public void step(float dt) {
        stepInternal(dt, false);
    }

    /** Debug/self-test: deterministic advance in 1/20 s steps (still honours pause). */
    public void advance(float seconds) {
        int steps = Math.max(0, Math.round(seconds / WARM_STEP));
        for (int i = 0; i < steps; i++) step(WARM_STEP);
    }

    // ------------------------------------------------------------------ player-triggered extras

    /**
     * Click on the plate (plate-local logical px): a plume from the cursor, embers in lava.
     * Returns true only when a plume fired (not paused, outside the 0.35 s cooldown).
     */
    public boolean click(float plateX, float plateY) {
        if (paused || time - lastClick < CLICK_COOLDOWN) return false;
        lastClick = time;
        boolean onFish = plateX >= FISH_SLOT_X && plateX < FISH_SLOT_X + FISH_SLOT_SIZE;
        if (theme.isLava()) {
            for (int i = 0; i < 18; i++) {
                Bubble b = spawn(Kind.EMBER, plateX + uniform(-2, 2), null, true);
                if (b != null) {
                    b.y = plateY;
                    b.vx = uniform(-12, 12);
                    b.vy = -uniform(16, 32);
                    b.size = 2;                     // starts as a hot 2px spark, cools to 1px
                    b.front = onFish;
                }
            }
            return true;
        }
        spawnClickGroup(Kind.SMALL, 5, plateX, plateY, onFish);
        spawnClickGroup(Kind.MEDIUM, 2, plateX, plateY, onFish);
        spawnClickGroup(Kind.MICRO, 6, plateX, plateY, onFish);
        return true;
    }

    private void spawnClickGroup(Kind kind, int n, float px, float py, boolean onFish) {
        for (int i = 0; i < n; i++) {
            float x = px + uniform(-3, 3);
            float y = py + uniform(0, 3) + i * 2;
            Bubble b = spawn(kind, x, y, true);
            if (b != null) {
                b.vy *= 1.3f;
                b.front = onFish;                   // clicked on the fish: bubbles come off it
            }
        }
    }

    /** Rewards claimed: one rush up the whole plate. Skipped when paused or reducedEffects. */
    public void claimBurst() {
        if (paused || reducedEffects) return;
        if (theme.isLava()) {
            for (int i = 0; i < 28; i++) {
                Bubble b = spawn(Kind.EMBER, null, null, true);
                if (b != null) {
                    b.vy *= 1.6f;
                    b.size = 2;
                    b.front = rng.nextFloat() < 0.3f;
                }
            }
            return;
        }
        for (int i = 0; i < 16; i++) {
            Kind kind = i % 4 == 0 ? Kind.MEDIUM : Kind.SMALL;
            float x = 4 + i * (W - 8) / 15f + uniform(-2, 2);
            float y = H + 1 + uniform(0, 6);
            Bubble b = spawn(kind, x, y, true);
            if (b != null) {
                b.vy *= 1.7f;
                b.front = rng.nextFloat() < 0.3f;
            }
        }
        for (int i = 0; i < 14; i++) {
            Bubble b = spawn(Kind.MICRO, null, H - 1 - uniform(0, 8), true);
            if (b != null) b.vy *= 1.6f;
        }
    }

    // ------------------------------------------------------------------ simulation

    private void stepInternal(float dt, boolean ignorePaused) {
        if (!(dt > 0f)) return;
        dt = Math.min(dt, MAX_DT);
        time += dt;
        if (paused && !ignorePaused) return;        // frozen: keep the last frame as a still

        // steady emission
        acc[Kind.MICRO.ordinal()] += 2.4f * theme.rate * theme.fizz * dt;
        acc[Kind.SMALL.ordinal()] += 1.2f * theme.rate * dt;
        acc[Kind.MEDIUM.ordinal()] += 0.35f * theme.rate * dt;
        acc[Kind.SNOW.ordinal()] += theme.snow * dt;
        acc[Kind.EMBER.ordinal()] += theme.embers * dt;
        if (theme.isLava()) acc[Kind.BLOB.ordinal()] += 0.25f * dt;
        for (Kind kind : Kind.values()) {
            int i = kind.ordinal();
            while (acc[i] >= 1f) {
                acc[i] -= 1f;
                spawn(kind, null, null, false);
            }
        }

        // plumes: the only "event" - a short column from one spot
        if (theme.rate > 0f) {
            plumeTimer -= dt;
            if (plumeTimer <= 0f) {
                float x = uniform(8, W - 9);
                int n = 4 + rng.nextInt(4);         // 4..7
                for (int i = 0; i < n; i++) {
                    Kind kind = rng.nextFloat() < 0.3f ? Kind.MEDIUM : Kind.SMALL;
                    spawn(kind, x + uniform(-1, 1), H + 1 + i * 3.5f, false);
                }
                plumeTimer = uniform(5.0f, 8.0f) / Math.max(theme.rate, 0.3f);
            }
        }

        // integrate
        int write = 0;
        for (int read = 0; read < bubbles.size(); read++) {
            Bubble b = bubbles.get(read);
            if (b.popping) {
                b.popAge += dt;
                if (b.popAge < POP_DURATION) bubbles.set(write++, b);
                continue;
            }
            b.age += dt;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.kind == Kind.MEDIUM && b.y <= SURFACE_Y) {
                b.popping = true;                   // vanilla bubble_pop frames, 0.25 s
                bubbles.set(write++, b);
                continue;
            }
            if (b.age >= b.life || b.y < -4 || b.y > H + 6 || b.x < -4 || b.x > W + 4) continue;
            bubbles.set(write++, b);
        }
        if (write < bubbles.size()) bubbles.subList(write, bubbles.size()).clear();
        peak = Math.max(peak, bubbles.size());
    }

    /** x/y null = the kind's default. Returns null when the cap rejects the spawn. */
    private Bubble spawn(Kind kind, Float xArg, Float yArg, boolean extra) {
        if (bubbles.size() >= (extra ? MAX_WITH_EXTRAS : MAX_BUBBLES)) return null;
        float x = xArg != null ? xArg : uniform(3, W - 4);
        Bubble b = new Bubble();
        b.kind = kind;
        b.x = x;
        switch (kind) {
            case MICRO -> {
                if (theme.microColours.length == 0) return null;
                b.y = yArg != null ? yArg : uniform(H * 0.45f, H - 2);   // fizz starts anywhere in the lower half
                b.vx = theme.drift;
                b.vy = -uniform(10, 18) * theme.rise;
                b.life = uniform(0.9f, 2.2f);
                b.amp = uniform(0.2f, 0.6f);
                b.freq = uniform(1.5f, 3.0f);
                b.phase = uniform(0, PHASE_RANGE);
                b.alpha = uniform(0.55f, 0.9f);
                b.size = 1;
                b.colour = theme.microColours[rng.nextInt(theme.microColours.length)];
            }
            case SMALL -> {
                b.y = yArg != null ? yArg : H - 1;
                b.vx = theme.drift;
                b.vy = -uniform(7, 12) * theme.rise;
                b.life = uniform(3.0f, 6.0f);
                b.amp = uniform(0.6f, 1.3f);
                b.freq = uniform(1.0f, 1.8f);
                b.phase = uniform(0, PHASE_RANGE);
                b.alpha = uniform(0.65f, 0.95f);
                b.size = 2;
            }
            case MEDIUM -> {
                b.y = yArg != null ? yArg : H + 2;
                b.vx = theme.drift * 0.6f;
                b.vy = -uniform(5, 8) * theme.rise;
                b.life = 30f;
                b.amp = uniform(1.0f, 2.0f);
                b.freq = uniform(0.5f, 0.9f);
                b.phase = uniform(0, PHASE_RANGE);
                b.alpha = 0.9f;
                b.size = new int[]{4, 5, 5, 6, 6, 7}[rng.nextInt(6)];
                b.front = rng.nextFloat() < 0.2f;   // a few pass in front of the fish, for depth
            }
            case SNOW -> {                          // deep-ocean marine snow: drifts DOWN
                b.y = -1;
                b.vx = uniform(-1.5f, 1.5f);
                b.vy = uniform(2, 4.5f);
                b.life = 40f;
                b.amp = uniform(0.5f, 1.5f);
                b.freq = uniform(0.2f, 0.5f);
                b.phase = uniform(0, PHASE_RANGE);
                b.alpha = uniform(0.35f, 0.6f);
                b.size = 1;
                b.colour = 0xCDD7E1;
            }
            case EMBER -> {                         // nether: sparks rising through lava
                b.y = H - 1;
                b.vx = uniform(-2, 2);
                b.vy = -uniform(9, 17);
                b.life = uniform(1.5f, 3.5f);
                b.amp = uniform(0.4f, 1.2f);
                b.freq = uniform(1.5f, 3.5f);
                b.phase = uniform(0, PHASE_RANGE);
                b.alpha = 1f;
                b.size = 1;
            }
            case BLOB -> {                          // nether: slow molten globules
                b.y = H + 3;
                b.vx = 0;
                b.vy = -uniform(3, 5);
                b.life = uniform(8, 14);
                b.amp = uniform(0.5f, 1.0f);
                b.freq = uniform(0.3f, 0.6f);
                b.phase = uniform(0, PHASE_RANGE);
                b.alpha = 1f;
                b.size = new int[]{2, 3, 3}[rng.nextInt(3)];
            }
        }
        bubbles.add(b);
        return b;
    }

    private float uniform(float lo, float hi) {
        return lo + rng.nextFloat() * (hi - lo);
    }
}
