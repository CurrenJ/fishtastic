package grill24.fishtastic.util;

import grill24.fishtastic.component.FishQuality;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Scripted celebration timeline for the rarest catches — the "hero moment" that plays instead of
 * the ordinary reward pop-off (see {@link FishingTarget#startCollectionAnimation}).
 *
 * <p>This class is deliberately pure: it holds a clock and answers questions about where the hero
 * item should be, how fast the rest of the minigame should run, and how hard the HUD should shake.
 * It performs no rendering and touches no Minecraft client state, which is what makes the timing
 * testable without a game running.
 *
 * <p><b>Why its own clock.</b> Everything else in the minigame interpolates between a
 * {@code previous} and {@code current} value sampled at the fixed 20 Hz {@code tick()} using
 * {@code partialTick}. Scaling that clock to get slow motion breaks the contract — the interpolant
 * would no longer span exactly one tick. Instead this timeline advances by real elapsed time in
 * tick units (the {@code deltaTime} {@code FishingMinigameAnimation#render} already computes) and
 * derives every value analytically from that one float. Nothing here needs interpolating.
 *
 * <p>Positions are expressed as fractions of screen height rather than pixels so the timeline stays
 * resolution independent; the renderer multiplies them out.
 */
public class CatchCelebration {

    /** How much ceremony a catch earns. */
    public enum Tier {
        /** Ordinary catch — no celebration, just the universal hitstop. */
        NONE,
        /** First-ever catch of a species. Shorter, softer version of the hero sequence. */
        DISCOVERY,
        /** Legendary quality. The full sequence. */
        HERO
    }

    public enum Phase { HITSTOP, LAUNCH, SUSPENSE, REVEAL, HANG, SETTLE, DONE }

    /**
     * Per-tier timeline shape. Durations are in game ticks (20 = 1 second) and are relative, not
     * absolute — each is the length of that phase, and the phase boundaries are their running sum.
     *
     * @param hitstop     hard freeze on impact, before anything moves
     * @param launch      hero item rises and grows, still an unreadable silhouette
     * @param suspense    item held at full size, still silhouetted, while the shake builds
     * @param reveal      flash, then the real texture resolves
     * @param hang        the hero item hangs revealed at peak size, slowly turning
     * @param settle      released into physics, shrinking away
     * @param peakScale   hero size at apex, as a multiple of a normal reward item
     * @param launchScale time scale applied through the rise and the suspense hold
     * @param holdScale   time scale from REVEAL through HANG (1.0 = no slow motion)
     * @param sparkles    sparkle count for the burst spawned at REVEAL
     * @param shakePixels HUD shake amplitude at impact, in GUI-scaled pixels
     */
    public record Timings(
            float hitstop,
            float launch,
            float suspense,
            float reveal,
            float hang,
            float settle,
            float peakScale,
            float launchScale,
            float holdScale,
            int sparkles,
            float shakePixels
    ) {
        public float launchStart()   { return hitstop; }
        public float suspenseStart() { return launchStart() + launch; }
        public float revealStart()   { return suspenseStart() + suspense; }
        public float hangStart()     { return revealStart() + reveal; }
        public float settleStart()   { return hangStart() + hang; }
        public float total()         { return settleStart() + settle; }
    }

    /**
     * The full sequence: freeze, a slow silhouetted rise, the reveal flash, a long hang at size,
     * then time ramps back up as it shrinks away. ~2.5 seconds end to end.
     *
     * <p>{@code peakScale} is a multiple of the normal reward item size, which renders at
     * {@code 2/16} of the bar's {@code 2 * screenHeight / 3} scale — about {@code screenHeight/12}
     * tall. 3.5x therefore fills roughly a third of the screen height, which reads as a hero item
     * without swallowing the whole HUD. Tune it live with {@code /fishtastic celebration test}.
     */
    public static final Timings HERO_TIMINGS =
            new Timings(3f, 9f, 14f, 12f, 32f, 35f, 3.5f, 0.25f, 0.25f, 75, 4f);

    /**
     * Roughly 60% of the hero timings, and no slow-motion hold — a first discovery should feel
     * noteworthy without stopping the game every time an early-game player finds a new common fish.
     */
    public static final Timings DISCOVERY_TIMINGS =
            new Timings(2f, 6f, 7f, 8f, 16f, 30f, 2.0f, 0.5f, 1.0f, 30, 2f);

    /**
     * The fishing bar renders at {@code 2 * screenHeight / 3}, so one unit of the bar's own
     * coordinate space is this fraction of the screen's height. Canonical definition — the
     * celebration converts {@link PhysicsSimulation} output through it, and
     * {@link FishingMinigameAnimation} converts target offsets through it.
     */
    public static final float BAR_SPACE_TO_SCREEN_FRACTION = 2f / 3f;

    /** Where the hero item hangs, as a fraction of screen height above centre. */
    private static final float HANG_OFFSET_Y = -0.10f;
    /** Length of the impact and reveal flashes, in ticks. */
    private static final float FLASH_DURATION = 3f;
    /** Peak rotation of the hang wobble, in degrees. */
    private static final float HANG_ROTATION_DEGREES = 12f;
    /** Full wobble cycles per second while hanging. */
    private static final float HANG_ROTATION_HZ = 0.35f;
    /** Fraction of the wind-down spent shrinking from hero size back to ordinary reward size. */
    private static final float SETTLE_SHRINK_FRACTION = 0.6f;
    /** How hard the suspense rumble peaks, relative to the impact shake. */
    private static final float TENSION_SHAKE_SCALE = 1.35f;
    /** A full revolution — the item ends the reveal facing the camera again, as it started. */
    private static final float REVEAL_TURN_DEGREES = 360f;
    /** Quarter turn: the point in the revolution where the item is edge-on and the swap is hidden. */
    private static final float REVEAL_SWAP_DEGREES = 90f;
    /**
     * Playback rate of the released item's arc, relative to a normal reward's. The simulation is
     * tuned for a small icon popping off a bar with up to 100 ticks to finish; a hero item launched
     * from mid-screen would spend several seconds drifting on that timing. Speeding up the whole
     * trajectory preserves its shape exactly while getting the item off screen promptly — the same
     * trick {@code FishingMinigameAnimation}'s BAIT_POP_SPEED_SCALE uses in the other direction.
     */
    private static final float SETTLE_POP_SPEED = 1.6f;
    /**
     * Vertical offset, in fractions of screen height from centre, past which the item is considered
     * gone. The screen's bottom edge is 0.5; the margin covers the item's own half-height.
     */
    private static final float OFF_SCREEN_OFFSET_Y = 0.6f;

    private final Tier tier;
    private final Timings timings;
    private final ItemStack heroStack;
    /** The caught target's vertical position at the moment of the catch, in fractions of screen height. */
    private final float startOffsetY;
    private final Random random;

    private float time = 0f;
    private boolean sparkleBurstPending = true;

    /**
     * Takes over the hero item's motion for the wind-down. Rather than retracing its arc back to
     * the bar, the item is released into the same {@link PhysicsSimulation} every ordinary reward
     * pops off with — so once its moment is over it tumbles away exactly like any other catch.
     */
    @Nullable private PhysicsSimulation settlePhysics = null;

    /**
     * Leftover fraction of a tick not yet fed to {@link #settlePhysics}. That simulation is
     * fixed-step at 20 Hz and interpolates between its last two states, but this timeline advances
     * by real frame time — so whole ticks are handed over as they accumulate and the remainder is
     * used as the interpolant, which is precisely what it's for.
     */
    private float settlePhysicsAccumulator = 0f;

    public CatchCelebration(Tier tier, ItemStack heroStack, float startOffsetY, Random random) {
        if (tier == Tier.NONE) {
            throw new IllegalArgumentException("CatchCelebration requires a tier above NONE");
        }
        this.tier = tier;
        this.timings = tier == Tier.HERO ? HERO_TIMINGS : DISCOVERY_TIMINGS;
        this.heroStack = heroStack;
        this.startOffsetY = startOffsetY;
        this.random = random;
    }

    // -------------------------------------------------------------------------
    // Tier resolution
    // -------------------------------------------------------------------------

    /**
     * Picks the tier a set of reward stacks earns.
     *
     * <p>Legendary outranks discovery, so a first-ever legendary fires the full sequence once
     * rather than stacking two celebrations.
     *
     * @param isUndiscovered tests whether a stack is a species the player has never caught.
     *                       Injected rather than reaching into the encyclopedia cache directly so
     *                       the precedence rules stay testable off-thread.
     */
    public static Tier resolveTier(List<ItemStack> rewards, Predicate<ItemStack> isUndiscovered) {
        if (rewards.isEmpty()) return Tier.NONE;

        FishQuality.Quality best = FishQualityHelper.bestQuality(rewards);
        if (best == FishQuality.Quality.LEGENDARY) return Tier.HERO;

        for (ItemStack stack : rewards) {
            if (!stack.isEmpty() && isUndiscovered.test(stack)) return Tier.DISCOVERY;
        }
        return Tier.NONE;
    }

    /**
     * The stack a celebration should show — the highest-quality reward, falling back to the first.
     * A treasure chest that also contains a legendary fish should put the fish on stage.
     */
    public static ItemStack pickHeroStack(List<ItemStack> rewards, Tier tier, Predicate<ItemStack> isUndiscovered) {
        if (rewards.isEmpty()) return ItemStack.EMPTY;

        if (tier == Tier.DISCOVERY) {
            for (ItemStack stack : rewards) {
                if (!stack.isEmpty() && isUndiscovered.test(stack)) return stack;
            }
        }

        ItemStack best = rewards.getFirst();
        FishQuality.Quality bestQuality = FishQualityHelper.getQuality(best);
        for (ItemStack stack : rewards) {
            FishQuality.Quality quality = FishQualityHelper.getQuality(stack);
            if (quality != null && (bestQuality == null || quality.ordinal() > bestQuality.ordinal())) {
                best = stack;
                bestQuality = quality;
            }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // Clock
    // -------------------------------------------------------------------------

    /** Advances the timeline. {@code deltaTicks} is real elapsed time in game-tick units. */
    public void advance(float deltaTicks) {
        if (deltaTicks <= 0f) return;
        time = Math.min(time + deltaTicks, timings.total());

        if (time < timings.settleStart()) return;
        if (settlePhysics == null) {
            // Released from the hang position with the same launch velocities and tumble a normal
            // reward gets. Its own coordinates start at the origin; the hang offset is added back
            // when the position is read.
            settlePhysics = new PhysicsSimulation(heroStack, 0f, 0f, random);
            return;
        }
        settlePhysicsAccumulator += deltaTicks * SETTLE_POP_SPEED;
        while (settlePhysicsAccumulator >= 1f) {
            settlePhysics.tick();
            settlePhysicsAccumulator -= 1f;
        }
    }

    /** Physics offset for the current frame, in fractions of screen height, or null before the wind-down. */
    @Nullable
    private Vector2f settleOffset() {
        if (settlePhysics == null) return null;
        Vector2f pos = settlePhysics.getInterpolatedPosition(settlePhysicsAccumulator);
        // PhysicsSimulation works in the bar's units with positive Y up; screen offsets are a
        // fraction of screen height with positive Y down.
        return new Vector2f(
                pos.x() * BAR_SPACE_TO_SCREEN_FRACTION,
                -pos.y() * BAR_SPACE_TO_SCREEN_FRACTION);
    }

    /**
     * True once the reveal has played and the sequence may be cut short.
     *
     * <p>Skipping is gated because the input that drives it is the same input that plays the
     * minigame. A player mashing the impulse key to control the bobber is still mashing it when the
     * catch lands, so an ungated skip fires almost immediately — cutting to the wind-down before
     * the item has even finished growing, and throwing away the reveal that the whole sequence
     * exists to deliver. Gating on the reveal means mashing can only shorten the dwell at the end,
     * never rob the payoff.
     */
    public boolean isSkippable() {
        Phase phase = getPhase();
        return phase == Phase.HANG || phase == Phase.SETTLE || phase == Phase.DONE;
    }

    /**
     * Cuts straight to the wind-down, if the reveal has already played. Juice the player can't
     * skip stops being juice and becomes a cutscene — but see {@link #isSkippable()} for why it
     * can't be skippable from the very first frame either.
     */
    public void skipToSettle() {
        if (!isSkippable()) return;
        if (time < timings.settleStart()) {
            time = timings.settleStart();
            sparkleBurstPending = false;
        }
    }

    /**
     * Done once the timeline runs out, or as soon as the released item has flown off screen —
     * whichever comes first.
     *
     * <p>The early exit is what lets the item leave under its own momentum instead of being
     * scaled or faded out. The launch velocity is randomised, so the flight time varies; the
     * timeline's settle duration is the ceiling for a slow roll rather than a fixed wait, which
     * mirrors how an ordinary reward's pop-off ends on {@code allOffScreen || maxDuration}.
     */
    public boolean isFinished() {
        return time >= timings.total() || hasLeftScreen();
    }

    /** True once the released item has fallen past the bottom of the screen. */
    private boolean hasLeftScreen() {
        Vector2f settle = settleOffset();
        return settle != null && HANG_OFFSET_Y + settle.y() > OFF_SCREEN_OFFSET_Y;
    }

    public Phase getPhase() {
        if (time >= timings.total())         return Phase.DONE;
        if (time >= timings.settleStart())   return Phase.SETTLE;
        if (time >= timings.hangStart())     return Phase.HANG;
        if (time >= timings.revealStart())   return Phase.REVEAL;
        if (time >= timings.suspenseStart()) return Phase.SUSPENSE;
        if (time >= timings.launchStart())   return Phase.LAUNCH;
        return Phase.HITSTOP;
    }

    /**
     * True while the rest of the minigame should hold still — remaining targets stop moving and
     * catch progress stops accumulating. Runs for the whole celebration so a second fish can't be
     * landed behind the hero item.
     */
    public boolean suppressesGameplay() {
        return !isFinished();
    }

    /**
     * Multiplier for the minigame's own per-frame physics (currently just the bobber, since
     * targets are frozen outright while {@link #suppressesGameplay()} holds). 0 during the impact
     * freeze, the tier's slow-motion rate through the middle, ramping back to 1 as it settles.
     */
    public float getTimeScale() {
        return switch (getPhase()) {
            case HITSTOP -> 0f;
            case LAUNCH, SUSPENSE -> timings.launchScale();
            case REVEAL, HANG -> timings.holdScale();
            case SETTLE  -> MathUtil.lerp(timings.holdScale(), 1f, settleProgress());
            case DONE    -> 1f;
        };
    }

    // -------------------------------------------------------------------------
    // Hero item transform
    // -------------------------------------------------------------------------

    /** Horizontal offset from screen centre, as a fraction of screen height. */
    public float getHeroOffsetX() {
        Vector2f settle = settleOffset();
        return settle == null ? 0f : settle.x();
    }

    /**
     * Vertical offset from screen centre, as a fraction of screen height. Negative is up.
     *
     * <p>DONE resolves the same way SETTLE does rather than falling through to the hang position:
     * a finished celebration isn't cleared until the next 20 Hz tick, so any frame drawn in between
     * would otherwise snap the item back up to its apex for an instant.
     */
    public float getHeroOffsetY() {
        // Once released, the simulation owns the item's position outright.
        Vector2f settle = settleOffset();
        if (settle != null) return HANG_OFFSET_Y + settle.y();

        return switch (getPhase()) {
            case HITSTOP -> startOffsetY;
            case LAUNCH  -> MathUtil.lerp(startOffsetY, HANG_OFFSET_Y, MathUtil.easeOutCubic(launchProgress()));
            default      -> HANG_OFFSET_Y;
        };
    }

    /** Hero size as a multiple of a normal reward item's on-screen size. */
    public float getHeroScale() {
        return switch (getPhase()) {
            case HITSTOP -> 1f;
            case LAUNCH  -> MathUtil.lerp(1f, timings.peakScale(), MathUtil.easeOutCubic(launchProgress()));
            case SUSPENSE, REVEAL, HANG -> timings.peakScale();
            case SETTLE, DONE -> settleScale();
        };
    }

    /**
     * Shrinks from hero size back to that of an ordinary reward and then stays there — the item
     * doesn't disappear by scaling away, it leaves because the physics arc carries it off screen.
     * The shrink finishes well before then, so most of the flight is at normal item size and reads
     * as the same pop-off any other catch gets.
     */
    private float settleScale() {
        return MathUtil.lerp(timings.peakScale(), 1f,
                MathUtil.easeInOutQuad(Math.min(1f, settleProgress() / SETTLE_SHRINK_FRACTION)));
    }

    /**
     * Hero rotation in degrees — a slow wobble once revealed, handing over to the simulation's own
     * tumble as soon as the item is released.
     */
    public float getHeroRotation() {
        if (settlePhysics != null) {
            return settlePhysics.getInterpolatedRotation(settlePhysicsAccumulator).z();
        }
        // Dead still until the fish is revealed — through the suspense hold the only motion is the
        // building shake, so the silhouette reads as something straining rather than drifting, and
        // through the turn itself the wobble would fight the spin.
        if (time < revealSwapTime()) return 0f;
        float elapsed = time - revealSwapTime();
        return (float) Math.sin(elapsed / 20f * HANG_ROTATION_HZ * 2 * Math.PI) * HANG_ROTATION_DEGREES;
    }

    /**
     * True while the hero item should render as an unreadable silhouette. The whole point of the
     * sequence: the prize is withheld until the reveal, so the payoff lands as a reveal rather than
     * as a fish that was legible the entire time.
     *
     * <p>The swap happens partway through the reveal turn rather than at its start — see
     * {@link #revealSwapTime()}.
     */
    public boolean isSilhouetted() {
        return time < revealSwapTime();
    }

    /**
     * The instant the silhouette becomes the real fish: a quarter of the way through the reveal
     * turn, when the item has spun 90° and is edge-on to the camera. At that moment it is an
     * infinitely thin line, so the substitution is invisible and the fish appears to have been
     * inside the silhouette all along — rather than one sprite cross-fading into another.
     */
    private float revealSwapTime() {
        return timings.revealStart() + timings.reveal() * (REVEAL_SWAP_DEGREES / REVEAL_TURN_DEGREES);
    }

    /**
     * How far through its turn the item is, in degrees. Runs one full revolution across the reveal
     * phase and is zero everywhere else, so the item both enters and leaves the turn square to the
     * camera.
     */
    private float revealSpinDegrees() {
        if (time < timings.revealStart() || time >= timings.hangStart()) return 0f;
        return (time - timings.revealStart()) / timings.reveal() * REVEAL_TURN_DEGREES;
    }

    /**
     * Horizontal scale multiplier for the hero item, which is how a Y-axis turn is expressed in a
     * flat GUI: the sprite is squeezed to nothing at 90° and stretched back out, exactly the trick
     * {@code FishingMinigameAnimation}'s fail animation uses for its spin. Goes negative through
     * the back half of the turn, mirroring the sprite — which is the correct read of a flat object
     * showing its reverse side.
     */
    public float getHeroFlipScaleX() {
        return (float) Math.cos(Math.toRadians(revealSpinDegrees()));
    }

    // -------------------------------------------------------------------------
    // Screen effects
    // -------------------------------------------------------------------------

    /** Full-screen white flash alpha — one at impact, one at the reveal. 0 when neither is running. */
    public float getFlashAlpha() {
        float impact = flashFalloff(time);
        // Fires on the edge-on frame, punctuating the substitution itself rather than the start of
        // the turn that hides it.
        float reveal = flashFalloff(time - revealSwapTime());
        return Math.max(impact, reveal);
    }

    private static float flashFalloff(float sinceFlash) {
        if (sinceFlash < 0f || sinceFlash >= FLASH_DURATION) return 0f;
        float t = sinceFlash / FLASH_DURATION;
        return (1f - t) * (1f - t);
    }

    /**
     * HUD shake offset in GUI-scaled pixels, decaying from impact through the end of the launch.
     * Two different frequencies so the motion doesn't read as a clean oscillation.
     */
    public float getShakeX() {
        return (float) Math.sin(time * 3.1f) * shakeAmplitude();
    }

    public float getShakeY() {
        return (float) Math.cos(time * 2.3f) * shakeAmplitude();
    }

    /**
     * Two separate shakes with a lull between them: the impact rattle decays away as the item
     * rises, then a second one builds through the suspense hold and cuts out the instant the item
     * is revealed. The lull is what makes the build read — a rumble that never stopped would just
     * be background noise by the time it mattered.
     */
    private float shakeAmplitude() {
        // Cuts dead on the edge-on frame, so the rumble stops at the same instant the fish appears
        // rather than partway through the turn that conceals the swap.
        if (time >= revealSwapTime()) return 0f;

        if (time < timings.suspenseStart()) {
            float remaining = 1f - (time / timings.suspenseStart());
            return timings.shakePixels() * remaining * remaining;
        }

        // Clamped because the build now runs past the end of the suspense phase and on through the
        // first quarter turn, holding at peak until the reveal lands.
        float build = Math.min(1f, (time - timings.suspenseStart()) / timings.suspense());
        return timings.shakePixels() * TENSION_SHAKE_SCALE * build * build;
    }

    /**
     * Returns true exactly once, on the first call at or after the reveal, so the caller can spawn
     * the sparkle burst. Polled from the render path, which may run many times per tick — hence the
     * one-shot latch rather than a phase comparison.
     */
    public boolean consumeSparkleBurst() {
        if (sparkleBurstPending && time >= revealSwapTime()) {
            sparkleBurstPending = false;
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Tier getTier() { return tier; }

    public Timings getTimings() { return timings; }

    public ItemStack getHeroStack() { return heroStack; }

    public int getSparkleCount() { return timings.sparkles(); }

    /** Elapsed timeline position in ticks. Exposed for tests and debug readouts. */
    public float getTime() { return time; }

    private float launchProgress() {
        return MathUtil.clamp((time - timings.launchStart()) / timings.launch(), 0f, 1f);
    }

    private float settleProgress() {
        return MathUtil.clamp((time - timings.settleStart()) / timings.settle(), 0f, 1f);
    }
}
