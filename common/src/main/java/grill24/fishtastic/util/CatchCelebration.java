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
     * The full sequence: freeze, a silhouetted rise, the reveal flash, then a long hold on the
     * revealed fish before it is released. The hold is 8 seconds by default (160 ticks) — long
     * enough to admire, and the player can end it at any time after a short grace period with the
     * impulse key or right-click (see {@link #skipToSettle()}), so it is never a cutscene.
     *
     * <p>Durations are real ticks: the celebration clock runs on real elapsed time. Only the
     * minigame's own bobber physics sees {@code launchScale} / {@code holdScale}.
     *
     * <p>{@code peakScale} is a multiple of the normal reward item size, which renders at
     * {@code 2/16} of the bar's {@code 2 * screenHeight / 3} scale — about {@code screenHeight/12}
     * tall. 3.5x therefore fills roughly a third of the screen height, which reads as a hero item
     * without swallowing the whole HUD. Tune it live with {@code /fishtastic celebration test}.
     */
    public static final Timings HERO_TIMINGS =
            new Timings(3f, 9f, 14f, 12f, 160f, 35f, 3.5f, 0.25f, 0.25f, 75, 4f);

    /**
     * A shorter rise and a shorter (5 second) hold than the hero moment, and no slow-motion for the
     * bobber — a first discovery should feel noteworthy without stopping the game every time an
     * early-game player finds a new common fish. Skippable the same way.
     */
    public static final Timings DISCOVERY_TIMINGS =
            new Timings(2f, 6f, 10f, 8f, 100f, 30f, 2.0f, 0.5f, 1.0f, 30, 2f);

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
    /** Peak opacity of the full-screen flash — less than 1 so it punctuates without ever fully whiting out the screen. */
    private static final float MAX_FLASH_ALPHA = 0.2f;
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
    private final Polish polish;
    private final boolean reducedEffects;
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
        this(tier, heroStack, startOffsetY, random, false);
    }

    /**
     * @param reducedEffects the player's accessibility setting: strips the flash, shake, dim,
     *                       confetti, sparkles, spinning rays, punch and hopping text, leaving the
     *                       silhouette reveal, a still glow and the name banner. Decided once here
     *                       rather than polled, so a celebration never changes character mid-play.
     */
    public CatchCelebration(Tier tier, ItemStack heroStack, float startOffsetY, Random random, boolean reducedEffects) {
        if (tier == Tier.NONE) {
            throw new IllegalArgumentException("CatchCelebration requires a tier above NONE");
        }
        this.tier = tier;
        this.timings = tier == Tier.HERO ? HERO_TIMINGS : DISCOVERY_TIMINGS;
        this.polish = tier == Tier.HERO ? HERO_POLISH : DISCOVERY_POLISH;
        this.reducedEffects = reducedEffects;
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

        ItemStack best = rewards.get(0);
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
        if (phase == Phase.SETTLE || phase == Phase.DONE) return true;
        return phase == Phase.HANG && time >= timings.hangStart() + SKIP_GRACE_TICKS;
    }

    /**
     * How long into the hold a skip stays ignored. With an 8 second hold the cost of an accidental
     * skip is much higher: a player still hammering the impulse key as the reveal lands would
     * otherwise cut the payoff off the instant it arrived. Half a second is long enough for the
     * mashing to stop and the reveal to register, and short enough not to be felt as a lock-out.
     */
    public static final float SKIP_GRACE_TICKS = 10f;

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
        if (reducedEffects) return 0f;
        float impact = flashFalloff(time);
        // Fires on the edge-on frame, punctuating the substitution itself rather than the start of
        // the turn that hides it.
        float reveal = flashFalloff(time - revealSwapTime());
        return Math.max(impact, reveal);
    }

    private static float flashFalloff(float sinceFlash) {
        if (sinceFlash < 0f || sinceFlash >= FLASH_DURATION) return 0f;
        float t = sinceFlash / FLASH_DURATION;
        return MAX_FLASH_ALPHA * (1f - t) * (1f - t);
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
        if (reducedEffects) return 0f;
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
    // Celebration polish — dim-and-release, reveal punch, glow and rays, banner
    //
    // Every tier gets the same layers; a Polish profile sets how loud they are and what colour
    // they wear (gold and loud for the hero moment, cool and gentle for a first discovery). All
    // pure functions of the same clock as everything above.
    //
    // Rates are per real tick (the celebration clock is real time). The punch, dim and banner
    // constants are snappy on purpose — they punctuate the reveal and are gone within a second.
    // The rays and the glow are what carry the long hold, so they breathe and turn gently.
    // -------------------------------------------------------------------------

    /**
     * How a tier dresses the reveal.
     *
     * @param dimMax          darkest the scene gets while the silhouette strains (overlay opacity)
     * @param punchAmplitude  overshoot as the real fish resolves, as a fraction of its size
     * @param rayAlpha        peak opacity of the sunburst
     * @param raySize         sunburst size multiplier
     * @param confettiCount   pieces in the reveal burst
     * @param confettiPalette 0xRRGGBB colours the confetti is drawn from
     * @param glowRgb         colour of the soft glow behind the fish
     * @param rayCoreRgb      colour of the main (larger) ray layer
     * @param rayOuterRgb     colour of the counter-rotating inner ray layer
     * @param tagText         the line under the fish's name
     * @param tagRgb          the tag's colour
     * @param wavePeriodTicks real ticks for the tag's bounce wave to sweep the line once
     */
    public record Polish(
            float dimMax,
            float punchAmplitude,
            float rayAlpha,
            float raySize,
            int confettiCount,
            int[] confettiPalette,
            int glowRgb,
            int rayCoreRgb,
            int rayOuterRgb,
            String tagText,
            int tagRgb,
            float wavePeriodTicks
    ) {}

    public static final Polish HERO_POLISH = new Polish(
            0.38f, 0.16f, 0.55f, 1.0f, 150,
            new int[]{0xFF5C7A, 0xFF9F1C, 0xFFD23F, 0x7BE35A, 0x2EC4B6, 0x3DD5F3, 0x3A86FF, 0x8B5CF6, 0xFF4FD8},
            0xFFD23F, 0xFFF1B0, 0xFF9F1C,
            "LEGENDARY CATCH!", 0xFFD23F, 32f);

    /** Roughly half the loudness, and cool where the hero is warm — a find, not a fanfare. */
    public static final Polish DISCOVERY_POLISH = new Polish(
            0.16f, 0.09f, 0.34f, 0.8f, 55,
            new int[]{0x3DD5F3, 0x2EC4B6, 0x3A86FF, 0x7BE35A, 0xFFFFFF, 0x8B5CF6},
            0x3DD5F3, 0xC8F6FF, 0x2EC4B6,
            "NEW SPECIES!", 0x3DD5F3, 22f);

    /** How quickly the dim lets go after the reveal (per tick) — near-instant so the light lands as a release. */
    private static final float DIM_RELEASE_RATE = 0.9f;
    private static final float PUNCH_DECAY = 1.4f;
    private static final float PUNCH_FREQUENCY = 3.6f;
    /**
     * Sunburst rotation: a fast whoosh on the reveal that decelerates into a gentle drift, so the
     * rays feel thrown out by the flash and then simply turn for the length of the hold. Degrees =
     * {@code SPIN_BURST * (1 - e^(-t / SPIN_BURST_TICKS)) + SPIN_DRIFT * t}.
     */
    private static final float RAY_SPIN_BURST_DEGREES = 24f;
    private static final float RAY_SPIN_BURST_TICKS = 10f;
    private static final float RAY_SPIN_DRIFT_DEGREES_PER_TICK = 1.8f;
    /** Slow breathing of the glow through the hold: fraction of its brightness, and radians per tick. */
    private static final float GLOW_BREATH_AMOUNT = 0.12f;
    private static final float GLOW_BREATH_RATE = 0.22f;
    /** Ticks after the reveal before the name banner starts to pop in, and how long the pop takes. */
    private static final float BANNER_DELAY = 0.6f;
    private static final float BANNER_POP_TICKS = 2.6f;
    public Polish getPolish() { return polish; }

    /** The player's reduced-effects setting, as captured when this celebration began. */
    public boolean isReducedEffects() { return reducedEffects; }

    /** Ticks since the silhouette became the real fish; negative before the reveal. */
    public float getTicksSinceReveal() { return time - revealSwapTime(); }

    /** Ticks since the reveal, for the polish effects. */
    private float revealClock() {
        return getTicksSinceReveal();
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = MathUtil.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Overshoot-and-settle ease (ease-out-back). */
    private static float easeOutBack(float t) {
        t = MathUtil.clamp(t, 0f, 1f);
        float s = 2.2f;
        return 1f + (s + 1f) * (float) Math.pow(t - 1f, 3) + s * (t - 1f) * (t - 1f);
    }

    /**
     * Opacity of a darkening overlay: builds from the launch through the suspense hold, so the
     * silhouette strains in a dimming room, then lets go almost instantly on the reveal. The
     * contrast is what makes the flash and rays read as light arriving.
     */
    public float getDimAlpha() {
        if (reducedEffects) return 0f;
        float swap = revealSwapTime();
        if (time < swap) return polish.dimMax() * smoothstep(timings.launchStart(), swap, time);
        return polish.dimMax() * (float) Math.exp(-revealClock() * DIM_RELEASE_RATE);
    }

    /**
     * Extra multiplier on the hero's size: 1 until the reveal, then a decaying ring-out around 1
     * — the fish overshoots as it resolves and settles. Kept out of {@link #getHeroScale()} so the
     * timeline's own scale contract (and its tests) stay untouched.
     */
    public float getHeroPunchScale() {
        if (reducedEffects || time < revealSwapTime()) return 1f;
        float tr = revealClock();
        return 1f + polish.punchAmplitude() * (float) (Math.exp(-PUNCH_DECAY * tr) * Math.cos(PUNCH_FREQUENCY * tr));
    }

    /** Swell-in-and-fade envelope shared by the glow and the rays, in [0, 1]. */
    private float lightEnvelope() {
        if (time < revealSwapTime()) return 0f;
        float rise = smoothstep(0f, 0.7f, revealClock());
        // Holds full through the whole hang and only fades as the item is released.
        float fade = 1f - smoothstep(timings.settleStart(), timings.settleStart() + timings.settle() * 0.3f, time);
        return rise * fade;
    }

    /**
     * Opacity of the spinning sunburst. Zero under reduced effects — a rotating fan of light is
     * precisely the motion that setting exists to remove.
     */
    public float getRayAlpha() {
        return reducedEffects ? 0f : polish.rayAlpha() * lightEnvelope();
    }

    /** Opacity of the soft glow behind the fish. Still under reduced effects, just dimmer. */
    public float getGlowAlpha() {
        // A slow breath keeps a long hold alive; a still glow under reduced effects.
        float breath = reducedEffects ? 1f
                : 1f - GLOW_BREATH_AMOUNT * (0.5f - 0.5f * (float) Math.cos(revealClock() * GLOW_BREATH_RATE));
        return polish.rayAlpha() * lightEnvelope() * breath * (reducedEffects ? 0.6f : 1f);
    }

    /** Sunburst rotation in degrees. Zero before the reveal. */
    public float getRaySpinDegrees() {
        if (reducedEffects || time < revealSwapTime()) return 0f;
        float t = revealClock();
        return RAY_SPIN_BURST_DEGREES * (1f - (float) Math.exp(-t / RAY_SPIN_BURST_TICKS)) + RAY_SPIN_DRIFT_DEGREES_PER_TICK * t;
    }

    /**
     * Scale of the name banner: pops in with overshoot shortly after the reveal, 0 before. Under
     * reduced effects it simply appears at full size and fades in (see {@link #getBannerAlpha()}).
     */
    public float getBannerScale() {
        if (time < revealSwapTime()) return 0f;
        float p = (revealClock() - BANNER_DELAY) / BANNER_POP_TICKS;
        if (reducedEffects) return p > 0f ? 1f : 0f;
        return p <= 0f ? 0f : easeOutBack(p);
    }

    /** Opacity of the name banner: solid through the hang, gone by the time the fish has left. */
    public float getBannerAlpha() {
        if (time < revealSwapTime()) return 0f;
        float in = smoothstep(0f, 0.4f, revealClock() - BANNER_DELAY);
        float out = 1f - smoothstep(timings.settleStart(), timings.settleStart() + timings.settle() * 0.35f, time);
        return in * out;
    }

    /**
     * How far the banner text's bounce wave has swelled, in [0, 1]: flat until the banner has
     * finished popping in, then rising to full height. Always zero under reduced effects.
     */
    public float getWaveAmplitude() {
        if (reducedEffects || time < revealSwapTime()) return 0f;
        float settled = BANNER_DELAY + BANNER_POP_TICKS;
        return smoothstep(settled, settled + 1.2f, revealClock());
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
