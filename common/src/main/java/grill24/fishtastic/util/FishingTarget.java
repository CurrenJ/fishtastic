package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.data.MovementParams;
import grill24.fishtastic.data.PhaseRule;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

public class FishingTarget {
    public enum TargetState {
        ACTIVE,
        ANIMATING_FAIL,
        ANIMATING_SUCCESS,
        COMPLETE
    }

    public enum TargetCategory {
        FISH,
        TREASURE
    }

    /**
     * Controls how the target moves along the bar.
     * DRIFT          — slow random wandering.
     * DART           — long pauses followed by eased fast bursts.
     * OSCILLATE      — rhythmic sine-wave bounce that speeds up as it's being caught.
     * FLEE           — drifts lazily until the bobber gets close, then darts away.
     * LUNGE          — holds perfectly still (telegraph), then fires a fast linear burst.
     * PULSE          — oscillation with a breathing amplitude envelope.
     * STUTTER        — discrete step-pause-step movement toward a wandering target.
     * INTERVAL_BURST — N sequential darts with short pauses, then a long rest.
     */
    public enum MovementPattern implements StringRepresentable {
        DRIFT, DART, OSCILLATE, FLEE, LUNGE, PULSE, STUTTER, INTERVAL_BURST;

        public static final com.mojang.serialization.Codec<MovementPattern> CODEC =
                StringRepresentable.fromEnum(MovementPattern::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // -------------------------------------------------------------------------
    // Difficulty (fixed for the lifetime of the target)
    // -------------------------------------------------------------------------

    private final float difficulty;

    // -------------------------------------------------------------------------
    // Movement parameters (re-applied on phase transition)
    // -------------------------------------------------------------------------

    // DRIFT
    private float minMoveIntervalTicks;
    private float maxMoveIntervalTicks;
    private float minSpeed;
    private float maxSpeed;

    // DART
    private float dartPauseMin;
    private float dartPauseMax;
    private float dartBurstProgressPerTick;

    // OSCILLATE
    private float oscBaseFrequency;
    private float oscRePeriodMin;
    private float oscRePeriodMax;
    private float oscAmplitudeMin;
    private float oscAmplitudeMax;

    // FLEE
    private float fleeThreshold;
    private float fleeSpeed;

    // LUNGE  (difficulty-scaled only; not exposed in MovementParams)
    private float lungeTelegraphMin;
    private float lungeTelegraphMax;
    private float lungeDistMin;
    private float lungeDistMax;
    private float lungeBurstProgressPerTick;

    // PULSE  — reuses osc params for the inner wave; adds envelope frequency
    private float pulseFrequency;

    // STUTTER  (difficulty-scaled only)
    private float stutterStepSize;
    private int stutterStepPauseTicks;
    private int stutterTargetInterval;

    // INTERVAL_BURST  (difficulty-scaled only)
    private int ibBurstCount;
    private float ibBurstProgressPerTick;
    private int ibPauseBetweenBursts;
    private float ibPauseAfterMin;
    private float ibPauseAfterMax;

    // Catch progress rates
    private final float catchProgressGain;
    private float catchProgressLoss;
    private float initialCatchProgress;

    // -------------------------------------------------------------------------
    // Phase / pattern tracking
    // -------------------------------------------------------------------------

    private final List<PhaseRule> phases; // sorted ascending by threshold
    private int activePhaseIndex;
    private MovementPattern currentPattern;

    // Phase-level controls (set when a phase is applied)
    private int patternTicksRemaining;    // -1 = no duration limit
    private float activeMinCatchProgress; // catch progress floor for this phase

    // -------------------------------------------------------------------------
    // Per-target movement state
    // -------------------------------------------------------------------------

    private float currentPosition;
    private float targetPosition;
    private float previousPosition;
    private float speed;

    // DRIFT / FLEE drift timing
    private int ticksSinceLastMove;
    private int ticksUntilNextMove;

    // DART state
    private boolean dartIsBursting;
    private int dartPauseTicks;
    private int dartNextPauseDuration;
    private float dartBurstStart;
    private float dartBurstEnd;
    private float dartBurstProgress;

    // OSCILLATE state  (also shared by PULSE for the inner wave)
    private float oscCenter;
    private float oscAmplitude;
    private float oscPhase;
    private int oscTicksSinceAnchor;
    private int oscNextRePeriod;

    // LUNGE state
    private boolean lungeIsFiring;
    private int lungeTelegraphTick;
    private int lungeNextTelegraphDuration;
    private float lungeBurstStart;
    private float lungeBurstEnd;
    private float lungeBurstProgress;

    // PULSE state
    private float pulsePhase;

    // STUTTER state
    private float stutterTarget;
    private int stutterStepPauseTick;
    private int stutterTicksSinceTarget;
    private boolean stutterIsPausing;

    // INTERVAL_BURST state  (ibPhase: 0 = post-seq rest, 1 = bursting, 2 = inter-burst pause)
    private int ibPhase;
    private int ibCurrentBurst;
    private float[] ibBurstTargets;
    private float ibBurstStart;
    private float ibBurstProgress;
    private int ibPauseTick;
    private int ibPauseDuration;

    // Catch progress
    private float catchProgress;

    // Position is constrained to [POSITION_MIN, POSITION_MAX] so a target can always be
    // reached by the bobber even at the very top or bottom of the bar.
    private static final float POSITION_MIN   = 0.05f;
    private static final float POSITION_MAX   = 0.95f;

    private static final float SHAKE_INTENSITY = 0.002f;
    private int shakeTick;

    // Animation state
    private TargetState state;
    private int animationTick;
    private int previousAnimationTick;

    private static final int COLLECTION_ANIMATION_DURATION = 30;
    private static final int SUCCESS_ANIMATION_MAX_DURATION = 100;

    private final List<ItemStack> rewardItems;
    private final TargetCategory category;
    private final Random random;
    private List<PhysicsSimulation> physicsSimulations;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public FishingTarget(List<ItemStack> rewardItems, TargetCategory category, Random random) {
        this(rewardItems, category, random, random.nextFloat(), 0.5f, List.of());
    }

    public FishingTarget(List<ItemStack> rewardItems, TargetCategory category, Random random, float initialPosition) {
        this(rewardItems, category, random, initialPosition, 0.5f, List.of());
    }

    public FishingTarget(List<ItemStack> rewardItems, TargetCategory category, Random random, float initialPosition, float difficulty) {
        this(rewardItems, category, random, initialPosition, difficulty, List.of());
    }

    /**
     * @param difficulty 0.0 = easiest, 1.0 = hardest.
     * @param phases     Phase rules defining movement patterns and optional param overrides.
     *                   Empty list falls back to difficulty-weighted random pattern selection.
     */
    public FishingTarget(List<ItemStack> rewardItems, TargetCategory category, Random random,
                         float initialPosition, float difficulty, List<PhaseRule> phases) {
        this.rewardItems = new ArrayList<>(rewardItems);
        this.category = category;
        this.random = random;
        this.ibBurstTargets = new float[5]; // max 5 bursts per INTERVAL_BURST sequence

        float d = Math.max(0f, Math.min(1f, difficulty));
        this.difficulty = d;
        this.catchProgressGain = 0.008f;

        // Fill empty phases with a single default phase using difficulty-weighted random pattern
        this.phases = phases.isEmpty()
                ? List.of(new PhaseRule(0f, List.of(resolvePattern(d, random.nextFloat(), null)),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                : phases.stream().sorted(Comparator.comparingDouble(PhaseRule::threshold)).toList();

        // Apply difficulty-scaled defaults first
        applyDifficultyDefaults(d);

        // Position state
        this.currentPosition = clampPos(initialPosition);
        this.targetPosition = currentPosition;
        this.previousPosition = currentPosition;
        this.speed = 0f;

        this.state = TargetState.ACTIVE;
        this.animationTick = 0;
        this.previousAnimationTick = 0;
        this.physicsSimulations = new ArrayList<>();
        this.shakeTick = 0;

        // Initialize phase 0 — applies phase params, picks initial pattern, inits movement state
        this.activePhaseIndex = -1;
        this.currentPattern = MovementPattern.DRIFT; // overwritten immediately by applyPhase
        applyPhase(0);

        // Set catch progress after phase 0 may have overridden initialCatchProgress
        this.catchProgress = this.initialCatchProgress;
    }

    // -------------------------------------------------------------------------
    // Phase management
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the highest-threshold phase whose threshold is &le; catchProgress.
     * Phases must be sorted ascending by threshold.
     */
    private int resolvePhaseIndex() {
        int result = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (catchProgress >= phases.get(i).threshold()) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }

    private void applyPhase(int idx) {
        boolean isInit = activePhaseIndex == -1;
        if (!isInit && idx == activePhaseIndex) return;

        PhaseRule phase = phases.get(idx);
        applyParams(phase.params().orElse(null));

        // Min catch progress floor for this phase
        activeMinCatchProgress = phase.minCatchProgress().orElse(0f);
        if (!isInit) {
            catchProgress = Math.max(catchProgress, activeMinCatchProgress);
        }

        MovementPattern prevPattern = currentPattern;
        List<MovementPattern> patterns = phase.patterns();
        currentPattern = patterns.isEmpty() ? MovementPattern.DRIFT
                : patterns.get(random.nextInt(patterns.size()));

        activePhaseIndex = idx;
        if (isInit || currentPattern != prevPattern) {
            reinitPatternState();
        }

        // Set pattern rotation timer AFTER reinit (reinit doesn't touch the timer)
        setPatternDurationTimer(phase);
    }

    private void setPatternDurationTimer(PhaseRule phase) {
        if (phase.patternDurationMin().isPresent()) {
            float min = phase.patternDurationMin().get();
            float max = phase.patternDurationMax().orElse(min);
            patternTicksRemaining = (int) (min + random.nextFloat() * (max - min));
        } else {
            patternTicksRemaining = -1;
        }
    }

    /** Re-rolls the current phase's pattern and resets movement state. */
    private void resampleCurrentPhasePattern() {
        PhaseRule phase = phases.get(activePhaseIndex);
        List<MovementPattern> patterns = phase.patterns();
        currentPattern = patterns.isEmpty() ? MovementPattern.DRIFT
                : patterns.get(random.nextInt(patterns.size()));
        reinitPatternState();
        setPatternDurationTimer(phase);
    }

    // -------------------------------------------------------------------------
    // Parameter application
    // -------------------------------------------------------------------------

    private void applyDifficultyDefaults(float d) {
        minMoveIntervalTicks      = lerp(60f,    5f,    d);
        maxMoveIntervalTicks      = lerp(120f,   25f,   d);
        minSpeed                  = lerp(0.002f, 0.012f, d);
        maxSpeed                  = lerp(0.008f, 0.040f, d);
        dartPauseMin              = lerp(50f,    20f,   d);
        dartPauseMax              = lerp(100f,   45f,   d);
        dartBurstProgressPerTick  = 1f / lerp(18f, 8f, d);
        oscBaseFrequency          = lerp(0.025f, 0.055f, d);
        oscRePeriodMin            = lerp(100f,   50f,   d);
        oscRePeriodMax            = lerp(160f,   80f,   d);
        oscAmplitudeMin           = 0.08f;
        oscAmplitudeMax           = 0.25f;
        fleeThreshold             = lerp(0.18f,  0.28f, d);
        fleeSpeed                 = lerp(0.008f, 0.025f, d);
        catchProgressLoss         = lerp(0.004f, 0.018f, d);
        initialCatchProgress      = lerp(0.6f,   0.25f, d);

        // LUNGE
        lungeTelegraphMin         = lerp(25f,    10f,   d);
        lungeTelegraphMax         = lerp(50f,    22f,   d);
        lungeDistMin              = lerp(0.25f,  0.38f, d);
        lungeDistMax              = lerp(0.55f,  0.75f, d);
        lungeBurstProgressPerTick = 1f / lerp(8f, 3f, d);

        // PULSE
        pulseFrequency            = lerp(0.008f, 0.030f, d);

        // STUTTER
        stutterStepSize           = lerp(0.025f, 0.080f, d);
        stutterStepPauseTicks     = Math.max(1, (int) lerp(8f, 2f, d));
        stutterTargetInterval     = Math.max(10, (int) lerp(80f, 25f, d));

        // INTERVAL_BURST  (2 bursts at d=0 → 5 bursts at d=1)
        ibBurstCount              = 2 + (int) (d * 3f);
        ibBurstProgressPerTick    = 1f / lerp(14f, 5f, d);
        ibPauseBetweenBursts      = Math.max(2, (int) lerp(12f, 3f, d));
        ibPauseAfterMin           = lerp(60f,   22f,   d);
        ibPauseAfterMax           = lerp(100f,  45f,   d);
    }

    private void applyParams(@Nullable MovementParams p) {
        if (p == null) return;
        p.driftIntervalMin().ifPresent(v -> minMoveIntervalTicks = v);
        p.driftIntervalMax().ifPresent(v -> maxMoveIntervalTicks = v);
        p.driftSpeedMin().ifPresent(v -> minSpeed = v);
        p.driftSpeedMax().ifPresent(v -> maxSpeed = v);
        p.dartPauseMin().ifPresent(v -> dartPauseMin = v);
        p.dartPauseMax().ifPresent(v -> dartPauseMax = v);
        p.dartBurstTicks().ifPresent(v -> dartBurstProgressPerTick = 1f / v);
        p.oscFrequency().ifPresent(v -> oscBaseFrequency = v);
        p.oscRePeriodMin().ifPresent(v -> oscRePeriodMin = v);
        p.oscRePeriodMax().ifPresent(v -> oscRePeriodMax = v);
        p.oscAmplitudeMin().ifPresent(v -> oscAmplitudeMin = v);
        p.oscAmplitudeMax().ifPresent(v -> oscAmplitudeMax = v);
        p.fleeThreshold().ifPresent(v -> fleeThreshold = v);
        p.fleeSpeed().ifPresent(v -> fleeSpeed = v);
        p.catchProgressLoss().ifPresent(v -> catchProgressLoss = v);
        p.initialCatchProgress().ifPresent(v -> initialCatchProgress = v);
    }

    private void reinitPatternState() {
        ticksSinceLastMove  = 0;
        ticksUntilNextMove  = getRandomMoveInterval();

        dartIsBursting           = false;
        dartPauseTicks           = 0;
        dartNextPauseDuration    = getRandomDartPause();
        dartBurstStart           = currentPosition;
        dartBurstEnd             = currentPosition;
        dartBurstProgress        = 0f;

        oscCenter           = randPos();
        oscAmplitude        = oscAmplitudeMin + random.nextFloat() * (oscAmplitudeMax - oscAmplitudeMin);
        oscPhase            = random.nextFloat() * (float) (Math.PI * 2);
        oscTicksSinceAnchor = 0;
        oscNextRePeriod     = getRandomOscPeriod();

        lungeIsFiring              = false;
        lungeTelegraphTick         = 0;
        lungeNextTelegraphDuration = (int) (lungeTelegraphMin + random.nextFloat() * (lungeTelegraphMax - lungeTelegraphMin));
        lungeBurstStart            = currentPosition;
        lungeBurstEnd              = currentPosition;
        lungeBurstProgress         = 0f;

        pulsePhase          = random.nextFloat() * (float) (Math.PI * 2);

        stutterTarget        = currentPosition;
        stutterStepPauseTick = 0;
        stutterTicksSinceTarget = 0;
        stutterIsPausing     = false;

        ibPhase         = 0; // start in post-sequence rest
        ibCurrentBurst  = 0;
        ibBurstStart    = currentPosition;
        ibBurstProgress = 0f;
        ibPauseTick     = 0;
        ibPauseDuration = (int) (ibPauseAfterMin + random.nextFloat() * (ibPauseAfterMax - ibPauseAfterMin));
    }

    // -------------------------------------------------------------------------
    // Pattern selection (for fallback single-phase construction)
    // -------------------------------------------------------------------------

    private static MovementPattern resolvePattern(float d, float roll, @Nullable MovementPattern explicit) {
        if (explicit != null) return explicit;
        float driftWeight     = lerp(0.70f, 0.10f, d);
        float dartWeight      = lerp(0.10f, 0.35f, d);
        float oscillateWeight = lerp(0.15f, 0.30f, d);
        if (roll < driftWeight) return MovementPattern.DRIFT;
        roll -= driftWeight;
        if (roll < dartWeight) return MovementPattern.DART;
        roll -= dartWeight;
        if (roll < oscillateWeight) return MovementPattern.OSCILLATE;
        return MovementPattern.FLEE;
    }

    public static MovementPattern pickRandom(float difficulty, float roll) {
        return resolvePattern(Math.max(0f, Math.min(1f, difficulty)), roll, null);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float randPos() {
        return POSITION_MIN + random.nextFloat() * (POSITION_MAX - POSITION_MIN);
    }

    private static float clampPos(float v) {
        return Math.max(POSITION_MIN, Math.min(POSITION_MAX, v));
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    public void tick(float bobberPosition, float bobberSize) {
        previousPosition = currentPosition;
        previousAnimationTick = animationTick;

        switch (state) {
            case ACTIVE -> {
                int newPhaseIdx = resolvePhaseIndex();
                if (newPhaseIdx != activePhaseIndex) {
                    applyPhase(newPhaseIdx);
                }
                // Pattern rotation countdown
                if (patternTicksRemaining > 0) {
                    patternTicksRemaining--;
                    if (patternTicksRemaining == 0) {
                        resampleCurrentPhasePattern();
                    }
                }
                switch (currentPattern) {
                    case DRIFT          -> tickDrift();
                    case DART           -> tickDart();
                    case OSCILLATE      -> tickOscillate();
                    case FLEE           -> tickFlee(bobberPosition, bobberSize);
                    case LUNGE          -> tickLunge();
                    case PULSE          -> tickPulse();
                    case STUTTER        -> tickStutter();
                    case INTERVAL_BURST -> tickIntervalBurst();
                }
            }
            case ANIMATING_FAIL -> {
                animationTick++;
                if (animationTick >= COLLECTION_ANIMATION_DURATION) state = TargetState.COMPLETE;
            }
            case ANIMATING_SUCCESS -> {
                animationTick++;
                for (PhysicsSimulation sim : physicsSimulations) sim.tick();
                boolean allOffScreen = physicsSimulations.stream().allMatch(PhysicsSimulation::isOffScreen);
                if (allOffScreen || animationTick >= SUCCESS_ANIMATION_MAX_DURATION) state = TargetState.COMPLETE;
            }
            case COMPLETE -> { /* waiting to be removed */ }
        }
    }

    // -------------------------------------------------------------------------
    // Movement pattern implementations
    // -------------------------------------------------------------------------

    private void tickDrift() {
        ticksSinceLastMove++;
        if (ticksSinceLastMove >= ticksUntilNextMove) {
            targetPosition = randPos();
            speed = minSpeed + random.nextFloat() * (maxSpeed - minSpeed);
            ticksSinceLastMove = 0;
            ticksUntilNextMove = getRandomMoveInterval();
        }
        moveToward(targetPosition, speed);
    }

    private void tickDart() {
        if (!dartIsBursting) {
            dartPauseTicks++;
            if (dartPauseTicks >= dartNextPauseDuration) {
                dartBurstStart    = currentPosition;
                dartBurstEnd      = pickDistantPosition(0.3f);
                dartBurstProgress = 0f;
                dartIsBursting    = true;
                dartPauseTicks    = 0;
            }
        } else {
            dartBurstProgress = Math.min(1f, dartBurstProgress + dartBurstProgressPerTick);
            currentPosition = lerp(dartBurstStart, dartBurstEnd, MathUtil.easeInOutQuad(dartBurstProgress));
            if (dartBurstProgress >= 1f) {
                currentPosition = dartBurstEnd;
                dartIsBursting = false;
                dartNextPauseDuration = getRandomDartPause();
            }
        }
    }

    private void tickOscillate() {
        oscTicksSinceAnchor++;
        if (oscTicksSinceAnchor >= oscNextRePeriod) {
            oscCenter    = randPos();
            oscAmplitude = oscAmplitudeMin + random.nextFloat() * (oscAmplitudeMax - oscAmplitudeMin);
            oscTicksSinceAnchor = 0;
            oscNextRePeriod     = getRandomOscPeriod();
        }
        float frequency = oscBaseFrequency + catchProgress * difficulty * 0.04f;
        oscPhase += frequency;
        currentPosition = clampPos(oscCenter + oscAmplitude * (float) Math.sin(oscPhase));
    }

    private void tickFlee(float bobberPosition, float bobberSize) {
        float bobberCenter = bobberPosition + bobberSize * 0.5f;
        float dist = Math.abs(currentPosition - bobberCenter);

        if (dist < fleeThreshold) {
            float fleeDir = Math.signum(currentPosition - bobberCenter);
            if (fleeDir == 0f) fleeDir = (random.nextFloat() > 0.5f) ? 1f : -1f;
            currentPosition = clampPos(currentPosition + fleeDir * fleeSpeed);
        } else {
            ticksSinceLastMove++;
            if (ticksSinceLastMove >= ticksUntilNextMove) {
                targetPosition = randPos();
                speed = minSpeed * (0.5f + random.nextFloat() * 0.5f);
                ticksSinceLastMove = 0;
                ticksUntilNextMove = getRandomMoveInterval();
            }
            moveToward(targetPosition, speed);
        }
    }

    private void tickLunge() {
        if (!lungeIsFiring) {
            // Telegraph: hold perfectly still while the counter winds up
            lungeTelegraphTick++;
            if (lungeTelegraphTick >= lungeNextTelegraphDuration) {
                lungeBurstStart    = currentPosition;
                lungeBurstEnd      = pickLungeTarget();
                lungeBurstProgress = 0f;
                lungeIsFiring      = true;
                lungeTelegraphTick = 0;
            }
        } else {
            // Instant linear burst toward the pre-chosen target
            lungeBurstProgress = Math.min(1f, lungeBurstProgress + lungeBurstProgressPerTick);
            currentPosition = lerp(lungeBurstStart, lungeBurstEnd, lungeBurstProgress);
            if (lungeBurstProgress >= 1f) {
                currentPosition = lungeBurstEnd;
                lungeIsFiring = false;
                lungeNextTelegraphDuration = (int) (lungeTelegraphMin + random.nextFloat() * (lungeTelegraphMax - lungeTelegraphMin));
            }
        }
    }

    private void tickPulse() {
        // Re-anchor the oscillation center periodically
        oscTicksSinceAnchor++;
        if (oscTicksSinceAnchor >= oscNextRePeriod) {
            oscCenter    = randPos();
            oscAmplitude = oscAmplitudeMin + random.nextFloat() * (oscAmplitudeMax - oscAmplitudeMin);
            oscTicksSinceAnchor = 0;
            oscNextRePeriod     = getRandomOscPeriod();
        }
        // Advance the amplitude envelope (0 → 1 → 0 breathing)
        pulsePhase += pulseFrequency;
        float envelope = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
        // Advance the inner oscillation
        float frequency = oscBaseFrequency + catchProgress * difficulty * 0.04f;
        oscPhase += frequency;
        currentPosition = clampPos(oscCenter + oscAmplitude * envelope * (float) Math.sin(oscPhase));
    }

    private void tickStutter() {
        // Periodically choose a new target to drift toward
        stutterTicksSinceTarget++;
        if (stutterTicksSinceTarget >= stutterTargetInterval) {
            stutterTarget = randPos();
            stutterTicksSinceTarget = 0;
        }

        if (stutterIsPausing) {
            stutterStepPauseTick++;
            if (stutterStepPauseTick >= stutterStepPauseTicks) {
                stutterIsPausing     = false;
                stutterStepPauseTick = 0;
            }
        } else {
            float dist = stutterTarget - currentPosition;
            if (Math.abs(dist) > stutterStepSize * 0.5f) {
                currentPosition += Math.signum(dist) * stutterStepSize;
                currentPosition  = clampPos(currentPosition);
                stutterIsPausing     = true;
                stutterStepPauseTick = 0;
            } else {
                currentPosition = stutterTarget;
            }
        }
    }

    private void tickIntervalBurst() {
        switch (ibPhase) {
            case 0 -> { // post-sequence rest
                ibPauseTick++;
                if (ibPauseTick >= ibPauseDuration) {
                    // Pre-calculate all burst destinations for this sequence
                    for (int i = 0; i < ibBurstCount && i < ibBurstTargets.length; i++) {
                        ibBurstTargets[i] = randPos();
                    }
                    ibCurrentBurst  = 0;
                    ibBurstStart    = currentPosition;
                    ibBurstProgress = 0f;
                    ibPauseTick     = 0;
                    ibPhase         = 1;
                }
            }
            case 1 -> { // burst toward ibBurstTargets[ibCurrentBurst]
                ibBurstProgress = Math.min(1f, ibBurstProgress + ibBurstProgressPerTick);
                currentPosition = lerp(ibBurstStart, ibBurstTargets[ibCurrentBurst],
                        MathUtil.easeInOutQuad(ibBurstProgress));
                if (ibBurstProgress >= 1f) {
                    currentPosition = ibBurstTargets[ibCurrentBurst];
                    ibCurrentBurst++;
                    if (ibCurrentBurst >= ibBurstCount) {
                        // Sequence complete — long rest
                        ibPauseDuration = (int) (ibPauseAfterMin + random.nextFloat() * (ibPauseAfterMax - ibPauseAfterMin));
                        ibPauseTick     = 0;
                        ibPhase         = 0;
                    } else {
                        // Short inter-burst pause before the next burst
                        ibPauseDuration = ibPauseBetweenBursts;
                        ibPauseTick     = 0;
                        ibPhase         = 2;
                    }
                }
            }
            case 2 -> { // inter-burst pause
                ibPauseTick++;
                if (ibPauseTick >= ibPauseDuration) {
                    ibBurstStart    = currentPosition;
                    ibBurstProgress = 0f;
                    ibPauseTick     = 0;
                    ibPhase         = 1;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared movement helpers
    // -------------------------------------------------------------------------

    private void moveToward(float target, float moveSpeed) {
        if (currentPosition == target) return;
        float distance = target - currentPosition;
        float movement = Math.signum(distance) * Math.min(Math.abs(distance), moveSpeed);
        currentPosition = clampPos(currentPosition + movement);
        if (Math.abs(currentPosition - target) < 0.001f) currentPosition = target;
    }

    private float pickDistantPosition(float minDist) {
        for (int i = 0; i < 5; i++) {
            float candidate = randPos();
            if (Math.abs(candidate - currentPosition) >= minDist) return candidate;
        }
        return randPos();
    }

    /** Picks a LUNGE destination within [lungeDistMin, lungeDistMax] of the current position. */
    private float pickLungeTarget() {
        float dist = lungeDistMin + random.nextFloat() * (lungeDistMax - lungeDistMin);
        float dir  = random.nextBoolean() ? 1f : -1f;
        float t    = currentPosition + dir * dist;
        // Bounce back off edges so we stay in [0, 1]
        if (t < 0f) t = -t;
        if (t > 1f) t = 2f - t;
        return clampPos(t);
    }

    private int getRandomMoveInterval() {
        return (int) (minMoveIntervalTicks + random.nextFloat() * (maxMoveIntervalTicks - minMoveIntervalTicks));
    }

    private int getRandomDartPause() {
        return (int) (dartPauseMin + random.nextFloat() * (dartPauseMax - dartPauseMin));
    }

    private int getRandomOscPeriod() {
        return (int) (oscRePeriodMin + random.nextFloat() * (oscRePeriodMax - oscRePeriodMin));
    }

    // -------------------------------------------------------------------------
    // Catch progress
    // -------------------------------------------------------------------------

    /**
     * Updates catch progress from the bobber–target overlap quality.
     *
     * @param overlapQuality 0.0 = target outside bobber; 1.0 = target perfectly centred.
     */
    public void updateCatchProgress(float overlapQuality) {
        if (overlapQuality > 0f) {
            // Centre bonus: 1× at the bobber edge, 2× at dead centre
            float centreMultiplier = 1.0f + overlapQuality;
            // Precision slowdown: the last 15% of progress requires increasingly fine control
            float slowFactor = catchProgress > 0.85f
                    ? lerp(1f, 0.2f, (catchProgress - 0.85f) / 0.15f)
                    : 1f;
            catchProgress = Math.min(1.0f, catchProgress + catchProgressGain * centreMultiplier * slowFactor);
            shakeTick++;
        } else {
            catchProgress = Math.max(activeMinCatchProgress, catchProgress - catchProgressLoss);
            shakeTick = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    public float getPosition() { return currentPosition; }

    public float getInterpolatedPosition(float partialTick) {
        return previousPosition + (currentPosition - previousPosition) * partialTick;
    }

    public float getCatchProgress() { return catchProgress; }

    public boolean isCaught() { return catchProgress >= 1.0f; }

    public boolean hasFailed() { return catchProgress <= 0.0f; }

    public MovementPattern getMovementPattern() { return currentPattern; }

    public TargetState getState() { return state; }

    public boolean isAnimationComplete() { return state == TargetState.COMPLETE; }

    public float getDifficulty() { return difficulty; }

    public TargetCategory getCategory() { return category; }

    public List<ItemStack> getAllRewardItems() { return new ArrayList<>(rewardItems); }

    public List<PhysicsSimulation> getPhysicsSimulations() { return physicsSimulations; }

    // -------------------------------------------------------------------------
    // Telegraph / burst progress getters (for animation squash-stretch)
    // -------------------------------------------------------------------------

    /** 0→1 as DART winds up before a burst; 0 while bursting or not DART. */
    public float getDartCoilProgress() {
        if (currentPattern != MovementPattern.DART || dartIsBursting || dartNextPauseDuration <= 0) return 0f;
        return Math.min(1f, (float) dartPauseTicks / dartNextPauseDuration);
    }

    /** 0→1 through a DART burst; 0 while pausing or not DART. */
    public float getDartBurstProgress() {
        if (currentPattern != MovementPattern.DART || !dartIsBursting) return 0f;
        return dartBurstProgress;
    }

    /** 0→1 as LUNGE telegraphs; 0 while firing or not LUNGE. */
    public float getLungeTelegraphProgress() {
        if (currentPattern != MovementPattern.LUNGE || lungeIsFiring || lungeNextTelegraphDuration <= 0) return 0f;
        return Math.min(1f, (float) lungeTelegraphTick / lungeNextTelegraphDuration);
    }

    // -------------------------------------------------------------------------
    // Shake / rotation for rendering
    // -------------------------------------------------------------------------

    public float getShakeOffset(float partialTick) {
        return (float) Math.sin((shakeTick + partialTick) * 2) * SHAKE_INTENSITY;
    }

    public float getShakeAngle(float partialTick, float baseFrequency, float frequencyMultiplier, float amplitude) {
        float frequency = baseFrequency + catchProgress * frequencyMultiplier;
        return (float) (Math.sin((shakeTick + partialTick) * frequency * (Math.PI * 2)) * amplitude);
    }

    // -------------------------------------------------------------------------
    // Animation
    // -------------------------------------------------------------------------

    public void startFailAnimation() {
        state = TargetState.ANIMATING_FAIL;
        animationTick = 0;
        previousAnimationTick = 0;
    }

    public void startCollectionAnimation(float x, float y) {
        state = TargetState.ANIMATING_SUCCESS;
        animationTick = 0;
        previousAnimationTick = 0;
        physicsSimulations.clear();
        for (ItemStack item : rewardItems) {
            physicsSimulations.add(new PhysicsSimulation(item, 0, 0, random));
        }
    }

    public float getFailSpinAngle(float partialTick) {
        if (state != TargetState.ANIMATING_FAIL) return 0f;
        float interpolatedTick = previousAnimationTick + (animationTick - previousAnimationTick) * partialTick;
        return MathUtil.easeInOutQuad(Math.min(1.0f, interpolatedTick / COLLECTION_ANIMATION_DURATION)) * 720f;
    }

    public float getCollectionScale(float partialTick) {
        if (state != TargetState.ANIMATING_FAIL) return 1.0f;
        float interpolatedTick = previousAnimationTick + (animationTick - previousAnimationTick) * partialTick;
        return 1.0f - MathUtil.easeInOutQuad(Math.min(1.0f, interpolatedTick / COLLECTION_ANIMATION_DURATION));
    }

    public ItemStack getDisplayItemStack() {
        if (state == TargetState.ACTIVE || state == TargetState.ANIMATING_FAIL) {
            return new ItemStack(category == TargetCategory.FISH
                    ? FishtasticItems.GENERIC_FISH
                    : FishtasticItems.REWARD_CHEST);
        }
        return rewardItems.isEmpty() ? ItemStack.EMPTY : rewardItems.get(0);
    }

    // -------------------------------------------------------------------------
    // Reset / mutation helpers
    // -------------------------------------------------------------------------

    public void reset() {
        currentPosition = randPos();
        targetPosition = currentPosition;
        previousPosition = currentPosition;
        speed = 0f;
        shakeTick = 0;

        applyDifficultyDefaults(difficulty);
        activePhaseIndex = -1;
        applyPhase(0);

        catchProgress = initialCatchProgress;
    }

    public void setPosition(float position) {
        currentPosition = clampPos(position);
        previousPosition = currentPosition;
    }

    public void setTargetPosition(float position) {
        targetPosition = clampPos(position);
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0f, speed);
    }
}
