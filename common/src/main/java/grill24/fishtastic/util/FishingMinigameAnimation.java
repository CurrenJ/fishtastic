package grill24.fishtastic.util;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticSounds;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import grill24.fishtastic.client.TutorialClientHandler;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.client.renderer.FishtasticTextureOutlineEffect;
import grill24.fishtastic.client.renderer.ZoneIconTextures;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class FishingMinigameAnimation implements ItemActivationAnimation {
    private int tickCount = 0;
    private final FishingMinigameState minigameState;
    private boolean isTutorial = false;
    private final Random sparkleRandom = new Random();

    private record SparkleBurst(List<SparkleParticle> particles, float targetYOffset) {}
    private final List<SparkleBurst> sparkleBursts = new ArrayList<>();

    // Intro animation state
    private boolean isIntro = true;
    private int introAnimationTick = 0;
    private static final int INTRO_ANIMATION_DURATION = 20; // 1 second at 20 TPS

    // Hide animation state
    private boolean isHiding = false;
    private int hideAnimationTick = 0;
    private static final int HIDE_ANIMATION_DURATION = 20; // 1 second at 20 TPS

    // Frame-rate physics tracking
    private float lastRenderTime = -1f;
    private boolean wasImpulseKeyDown = false;
    // Force applied per tick-unit while the impulse key is held.  Much smaller than the
    // tap impulse (0.04) so continuous hold doesn't instantly slam the bobber to the ceiling.
    private static final float HOLD_IMPULSE_STRENGTH = 0.012f;
    private float inputForceMultiplier = 1.0f;

    // Equipped charm (client-side only; drives the crystal-ball rarity outline)
    @Nullable private CharmEffect equippedCharmEffect = null;

    // Angler's Almanac preview: the top-weighted fish (highest first) under current conditions,
    // or empty if no charm revealing it is equipped. Rendered off to the right of the minigame bar.
    private List<ItemStack> topWeightedFishPreviews = List.of();

    // Currently equipped bait/hook/charm itemstacks (client-side only, for the gear readout
    // rendered off to the left of the minigame bar, mirroring the almanac preview on the right).
    @Nullable private ItemStack equippedBaitStack = null;
    @Nullable private ItemStack equippedHookStack = null;
    @Nullable private ItemStack equippedCharmStack = null;

    // Set once, the first time a catch is about to empty the equipped bait stack (i.e. its count
    // was 1 at minigame start — the same condition FishingMinigameManager#consumeBait uses
    // server-side to detect depletion). We predict it client-side rather than waiting on the
    // server's confirmation because that confirmation only arrives after sendMinigameResults(),
    // by which point this animation instance is normally already hidden/discarded.
    private boolean baitPopTriggered = false;
    @Nullable private PhysicsSimulation baitDepletedSimulation = null;
    private int baitDepletedAnimationTick = 0;
    private static final int BAIT_DEPLETED_ANIMATION_MAX_DURATION = 100;
    // Scales down the rendered pop-off motion relative to a full reward item's (see
    // renderBaitDepletedAnimation) — tune this to make the bait icon's pop faster/slower.
    private static final float BAIT_POP_SPEED_SCALE = 0.4f;

    // Zone(s) the current cast resolved to (server-computed, see FishProfile.Zone#resolve),
    // rendered as a small icon stack tucked against the bar's top-right corner — the mirror image
    // of the equipped-gear column on the left. Usually one zone, occasionally two (e.g. a mountain
    // river is both RIVER and HIGH_ALTITUDE at once).
    @Nullable private Set<FishProfile.Zone> currentZones = null;

    private static final int SIDE_PANEL_ICON_SIZE = 24;
    private static final int SIDE_PANEL_MARGIN = 48;
    // Vertical gap between stacked almanac preview icons.
    private static final int ALMANAC_ICON_GAP = 4;
    // Gear readout (hook/charm) renders smaller than the almanac preview, tucked right up
    // against the bar's corner rather than out at the screen edge.
    private static final int GEAR_ICON_SIZE = 16;
    private static final int GEAR_ICON_GAP = 4;
    private static final int GEAR_PANEL_GAP = 6;
    // Zone art is 32x32 native. Keep the on-screen size a whole multiple of it so each source
    // texel lands on the same number of screen pixels at every GUI scale.
    private static final int ZONE_ICON_TEXTURE_PX = ZoneIconTextures.TEXTURE_PX;
    private static final int ZONE_ICON_SCALE = 1;
    private static final int ZONE_ICON_SIZE = ZONE_ICON_TEXTURE_PX * ZONE_ICON_SCALE;
    // Ticks the charm icon waits after the hook icon before starting its own slide.
    private static final float GEAR_STAGGER_DELAY_TICKS = 3f;

    // Track caught targets BEFORE they get removed
    private final List<Integer> caughtTargetIndices = new ArrayList<>();

    // ---- Catch celebration ----
    // The hero-moment sequence for the rarest catches. Only one runs at a time: while it's active
    // the rest of the minigame is frozen (see the guard in tick()), so a second target can't be
    // landed behind it.
    @Nullable private CatchCelebration celebration = null;

    /**
     * Species already celebrated during this minigame session, so hooking two of the same brand-new
     * fish in one cast only fires the discovery sequence once. The encyclopedia cache can't do this
     * for us — it isn't updated until the results reach the server, which happens after this
     * animation has already hidden.
     */
    private final Set<Identifier> celebratedSpecies = new HashSet<>();

    /**
     * Species in this session the player had never caught when they cast, sent by the server with
     * the start packet. The client can't work this out for itself: {@code FishEncyclopediaClientCache}
     * is populated only while the encyclopedia screen is open — see the handler swap in
     * {@code FishEncyclopediaScreen} — so consulting it during normal fishing reports every species
     * as never-caught and makes every catch look like a first discovery.
     */
    private Set<Identifier> undiscoveredSpecies = Set.of();

    /**
     * Ceiling on how long a celebration may hold the minigame frozen, in ticks — comfortably past
     * the longest sequence (50 ticks) so it never fires during normal play. See the watchdog note
     * in {@link #tick()}.
     */
    private static final int CELEBRATION_WATCHDOG_TICKS = 200;
    private int celebrationWatchdogTicks = 0;

    /**
     * Converts offsets between the bar's own coordinates and the fraction-of-screen-height space
     * {@link CatchCelebration} works in. Defined there, since the celebration converts its physics
     * output through the same factor.
     */
    private static final float BAR_SPACE_TO_SCREEN_FRACTION = CatchCelebration.BAR_SPACE_TO_SCREEN_FRACTION;

    // ---- Anticipation audio ----
    // A rising pitch ladder over the last stretch of catch progress — the "you're about to land it"
    // cue. Deliberately fires for every target, not just rare ones, so it reads as proximity to a
    // catch rather than as a rarity tell that would spoil the celebration's reveal.
    private static final float ANTICIPATION_THRESHOLD = 0.85f;
    private static final int ANTICIPATION_INTERVAL_TICKS = 2;
    private static final float ANTICIPATION_MIN_PITCH = 1.2f;
    private static final float ANTICIPATION_MAX_PITCH = 2.0f;
    private int lastAnticipationTick = -ANTICIPATION_INTERVAL_TICKS;

    public FishingMinigameAnimation() {
        this.minigameState = new FishingMinigameState(LAYOUT.bobberSize(), (1.0f - LAYOUT.bobberSize()) / 2f);

        // Add some example targets
        Random random = new Random();
        minigameState.addTarget(new FishingTarget(List.of(new ItemStack(Items.STONE)), FishingTarget.TargetCategory.TREASURE, random, 0.3f));
        minigameState.addTarget(new FishingTarget(List.of(new ItemStack(Items.DIAMOND)), FishingTarget.TargetCategory.TREASURE, random, 0.6f));
        minigameState.addTarget(new FishingTarget(List.of(new ItemStack(Items.GOLD_INGOT)), FishingTarget.TargetCategory.TREASURE, random, 0.8f));
    }

    /**
     * Dev tooling: stages a celebration on its own, without fishing for one.
     *
     * <p>The sequence is entirely timing — phase lengths, the peak scale, how hard the HUD shakes —
     * and none of that can be judged except by watching it. Waiting on a real legendary to evaluate
     * a tweak would make each iteration minutes long instead of seconds.
     *
     * <p>Skips {@link #resolveCelebrationTier} deliberately: the caller names the tier outright, so
     * the discovery variant can be previewed without needing a species the player has never caught.
     */
    public static FishingMinigameAnimation createCelebrationPreview(CatchCelebration.Tier tier, ItemStack hero) {
        FishingMinigameAnimation animation = new FishingMinigameAnimation();
        animation.isIntro = false;

        // One already-retired target, so the ordinary "everything finished, hide the bar" check
        // still fires once the celebration ends instead of leaving the preview on screen forever.
        List<FishingTarget> targets = animation.getMinigameState().getTargets();
        targets.clear();
        FishingTarget target = new FishingTarget(
                List.of(hero), FishingTarget.TargetCategory.FISH, new Random(), 0.5f);
        target.startCelebrationHandoff();
        targets.add(target);

        animation.celebration = new CatchCelebration(tier, hero, 0f, animation.sparkleRandom);
        return animation;
    }

    @Override
    public boolean isActive() {
        // Animation is active until hide animation completes
        return !isHiding || hideAnimationTick < HIDE_ANIMATION_DURATION;
    }

    private static int sparkleCountForTarget(FishingTarget target) {
        if (target.getCategory() == FishingTarget.TargetCategory.TREASURE) {
            return 16;
        }
        FishQuality.Quality best = FishQualityHelper.bestQuality(target.getAllRewardItems());
        if (best == null) return 4; // vanilla fish
        return switch (best) {
            case COMMON    -> 0;
            case UNCOMMON  -> 3;
            case RARE      -> 12;
            case EPIC      -> 30;
            case LEGENDARY -> 75;
        };
    }

    private void tickSparkles() {
        for (SparkleBurst burst : sparkleBursts) {
            burst.particles().forEach(SparkleParticle::tick);
            burst.particles().removeIf(p -> !p.isAlive());
        }
        sparkleBursts.removeIf(burst -> burst.particles().isEmpty());
    }

    @Override
    public void tick() {
        tickCount++;
        tickSparkles();

        // If in intro, advance intro animation
        if (isIntro) {
            introAnimationTick++;
            if (introAnimationTick >= INTRO_ANIMATION_DURATION) {
                isIntro = false;
            }
            return; // Don't update minigame state or targets during intro animation
        }

        // If hiding, advance hide animation
        if (isHiding) {
            hideAnimationTick++;
            return; // Don't update minigame state or targets during hide animation
        }

        // A celebration owns the screen while it runs: targets hold still, catch progress stops
        // accumulating, and the bait pop-off waits its turn. The celebration itself advances on its
        // own clock in render(), not here — see CatchCelebration's class docs for why.
        //
        // That dependency on render() is also why the watchdog exists. This tick runs at a steady
        // 20 Hz no matter what, but the HUD layer the celebration renders from does not — hiding
        // the GUI stops it outright. Without a ceiling on how long a celebration may hold the
        // minigame frozen, a player who hid the HUD at the wrong moment would strand the session
        // and never receive the catch, since results are only sent once the bar hides.
        if (celebration != null) {
            celebrationWatchdogTicks++;
            if (!celebration.isFinished() && celebrationWatchdogTicks < CELEBRATION_WATCHDOG_TICKS) return;
            celebration = null;
            celebrationWatchdogTicks = 0;
        }

        // No freeze on an ordinary catch. A brief one was tried here to give the catch some impact,
        // but this early return also skips minigameState.tick(), which is what advances the reward's
        // PhysicsSimulation inside FishingTarget#tick — so the item spawned visible and then hung
        // motionless for the duration before launching. Freezing the one thing the player is looking
        // at reads as a hitch, not an impact. The tiered celebrations keep their own HITSTOP phase,
        // where the held item is a silhouette that is meant to be still.

        // Only update minigame state and targets when not in intro or hiding
        minigameState.tick();

        // minigameState.tick() already no-ops target movement while paused, but catch-progress
        // below is computed independently from the bobber's (frozen) swept range — without this
        // guard it keeps accumulating every tick if the bobber happens to be parked over a target
        // when a tutorial stage pauses the minigame.
        if (minigameState.isPaused()) return;

        // Update catch progress for all targets based on collision. Bobber physics run once per
        // render frame (see updatePhysics calls in render()) while this tick only fires at 20 Hz,
        // so we check against the full envelope of bobber centers visited since the last tick
        // (getSweptMinPosition/MaxPosition) rather than a single instantaneous position — otherwise
        // a fast bobber flick could pass straight through a narrow target between two tick samples.
        final float bobberRadius = minigameState.getBobberSize() * 0.5f;
        final float sweptCenterMin = minigameState.getSweptMinPosition() + bobberRadius;
        final float sweptCenterMax = minigameState.getSweptMaxPosition() + bobberRadius;

        // Never remove targets from the list — doing so shifts indices and breaks the caught-index
        // tracking that is sent to the server.  Targets stay in their original slot forever; we
        // just stop updating them once they are no longer ACTIVE.
        boolean anyOngoing = false;
        float highestActiveProgress = 0f;
        List<FishingTarget> targets = minigameState.getTargets();
        for (int targetIndex = 0; targetIndex < targets.size(); targetIndex++) {
            FishingTarget target = targets.get(targetIndex);

            if (target.getState() == FishingTarget.TargetState.ACTIVE) {
                float targetPosition = target.getPosition();
                float distToCenter;
                if (targetPosition < sweptCenterMin) {
                    distToCenter = sweptCenterMin - targetPosition;
                } else if (targetPosition > sweptCenterMax) {
                    distToCenter = targetPosition - sweptCenterMax;
                } else {
                    distToCenter = 0f; // the bobber's center passed directly over the target at some point this tick
                }
                float overlapQuality = distToCenter < bobberRadius ? 1f - distToCenter / bobberRadius : 0f;
                target.updateCatchProgress(overlapQuality);

                if (target.isCaught()) {
                    float targetYOffset = (target.getPosition() - 0.5f) * LAYOUT.itemMaxYOffset();
                    caughtTargetIndices.add(targetIndex);

                    CatchCelebration.Tier tier = resolveCelebrationTier(target);
                    if (tier != CatchCelebration.Tier.NONE) {
                        startCelebration(target, tier, targetYOffset);
                    } else {
                        spawnSparkleBurst(sparkleCountForTarget(target), targetYOffset);
                        target.startCollectionAnimation(0, 0);
                        playCatchSound(target);
                    }

                    if (!baitPopTriggered && equippedBaitStack != null && equippedBaitStack.getCount() == 1) {
                        baitPopTriggered = true;
                        baitDepletedSimulation = new PhysicsSimulation(equippedBaitStack.copy(), 0, 0, sparkleRandom);
                        equippedBaitStack = null;
                    }
                } else if (target.hasFailed()) {
                    target.startFailAnimation();
                } else {
                    anyOngoing = true;
                    highestActiveProgress = Math.max(highestActiveProgress, target.getCatchProgress());
                }
            }
        }
        minigameState.resetSweptRange();
        // Driven off the single closest-to-caught target so a multi-target cast doesn't stack
        // overlapping ladders on top of each other.
        playAnticipationBlip(highestActiveProgress);

        if (baitDepletedSimulation != null) {
            baitDepletedAnimationTick++;
            baitDepletedSimulation.tick();
            if (baitDepletedSimulation.isOffScreen() || baitDepletedAnimationTick >= BAIT_DEPLETED_ANIMATION_MAX_DURATION) {
                baitDepletedSimulation = null;
            }
        }

        // Hide once every target has fully completed its animation (and the bait pop-off, if any).
        //
        // The celebration has to be checked here as well as at the top of this method. A catch
        // starts one partway through the loop above, so on that one tick execution reaches this
        // point with a celebration freshly created — and the caught target is already COMPLETE,
        // having handed its reward over. Without this term the bar would begin hiding on the very
        // tick the celebration starts, and the isHiding branch above returns before the celebration
        // guard ever runs, so the whole overlay would be torn down mid-sequence.
        boolean allComplete = !targets.isEmpty()
                && targets.stream().allMatch(FishingTarget::isAnimationComplete)
                && baitDepletedSimulation == null
                && celebration == null;
        if (!anyOngoing && allComplete) {
            if (!isHiding) {
                // Send results to server before hiding (via reflection to avoid client dependency)
                try {
                    Class<?> handlerClass = Class.forName("grill24.fishtastic.client.FishingMinigameClientHandler");
                    java.lang.reflect.Method sendMethod = handlerClass.getMethod("sendMinigameResults");
                    sendMethod.invoke(null);
                } catch (Exception e) {
                    // Silently fail - this is expected on server side
                }
            }
            isHiding = true;
        }
    }

    /**
     * Decides how much ceremony a just-caught target earns. Only fish qualify for the discovery
     * tier — treasure and trash have no encyclopedia entry, so "never caught before" is meaningless
     * for them and {@code getCatchCount} would read 0 for every rock ever pulled up.
     */
    private CatchCelebration.Tier resolveCelebrationTier(FishingTarget target) {
        if (celebration != null) return CatchCelebration.Tier.NONE; // one at a time
        boolean isFish = target.getCategory() == FishingTarget.TargetCategory.FISH;
        return CatchCelebration.resolveTier(
                target.getAllRewardItems(),
                stack -> isFish && isUncelebratedNewSpecies(stack));
    }

    /** True if this stack is a species the player has never caught and hasn't already celebrated this cast. */
    private boolean isUncelebratedNewSpecies(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return !celebratedSpecies.contains(id) && undiscoveredSpecies.contains(id);
    }

    /**
     * Hands a caught target over to a celebration: the sequence takes ownership of presenting the
     * reward, so the target retires without its usual physics pop-off. The sparkle burst is
     * deliberately not spawned here — it waits for the reveal, which is the whole point of the
     * sequence.
     */
    private void startCelebration(FishingTarget target, CatchCelebration.Tier tier, float targetYOffset) {
        List<ItemStack> rewards = target.getAllRewardItems();
        boolean isFish = target.getCategory() == FishingTarget.TargetCategory.FISH;
        ItemStack hero = CatchCelebration.pickHeroStack(
                rewards, tier, stack -> isFish && isUncelebratedNewSpecies(stack));

        celebratedSpecies.add(BuiltInRegistries.ITEM.getKey(hero.getItem()));
        // Bar-space offsets are negated on the way to screen space (the renderer translates by
        // -targetYOffset), and one bar unit is BAR_SPACE_TO_SCREEN_FRACTION of the screen height.
        celebration = new CatchCelebration(
                tier, hero.copy(), -targetYOffset * BAR_SPACE_TO_SCREEN_FRACTION, sparkleRandom);
        target.startCelebrationHandoff();

        SoundEvent sound = tier == CatchCelebration.Tier.HERO
                ? FishtasticSounds.CATCH_LEGENDARY.value()
                : FishtasticSounds.NEW_SPECIES_DISCOVERED.value();
        playUiSound(sound, 1.0f);

        if (tier == CatchCelebration.Tier.DISCOVERY) {
            // The first-catch banner arrives seconds later and would otherwise replay this same
            // fanfare; claiming it here keeps the announcement on the reveal, where the player is
            // actually looking.
            QuestProgressNotificationManager.claimDiscoveryFanfare(
                    BuiltInRegistries.ITEM.getKey(hero.getItem()));
        }
    }

    /** Spawns a sparkle burst centred on a bar-space vertical offset. */
    private void spawnSparkleBurst(int sparkleCount, float targetYOffset) {
        List<SparkleParticle> burst = new ArrayList<>();
        for (int i = 0; i < sparkleCount; i++) {
            burst.add(new SparkleParticle(0, 0, sparkleRandom, 20 + sparkleRandom.nextInt(15)));
        }
        sparkleBursts.add(new SparkleBurst(burst, targetYOffset));
    }

    /**
     * Plays the rising-pitch "nearly there" ladder over the last stretch of catch progress. The
     * window matches {@code FishingTarget}'s own precision slowdown, which already stretches these
     * final ticks out — so the ladder lands on a beat the player can feel rather than one invented
     * for the audio.
     */
    private void playAnticipationBlip(float progress) {
        if (progress < ANTICIPATION_THRESHOLD) return;
        if (tickCount - lastAnticipationTick < ANTICIPATION_INTERVAL_TICKS) return;
        lastAnticipationTick = tickCount;

        float t = MathUtil.clamp((progress - ANTICIPATION_THRESHOLD) / (1f - ANTICIPATION_THRESHOLD), 0f, 1f);
        playUiSound(FishtasticSounds.QUEST_PROGRESS.value(),
                MathUtil.lerp(ANTICIPATION_MIN_PITCH, ANTICIPATION_MAX_PITCH, t));
    }

    private static void playUiSound(SoundEvent sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    /** Plays the rarity-specific catch jingle for a just-caught target's best reward. */
    private void playCatchSound(FishingTarget target) {
        FishQuality.Quality bestQuality = FishQualityHelper.bestQuality(target.getAllRewardItems());
        if (bestQuality == null) {
            bestQuality = FishQuality.Quality.COMMON;
        }

        SoundEvent sound = switch (bestQuality) {
            case COMMON -> FishtasticSounds.CATCH_COMMON.value();
            case UNCOMMON -> FishtasticSounds.CATCH_UNCOMMON.value();
            case RARE -> FishtasticSounds.CATCH_RARE.value();
            case EPIC -> FishtasticSounds.CATCH_EPIC.value();
            case LEGENDARY -> FishtasticSounds.CATCH_LEGENDARY.value();
        };

        playUiSound(sound, 1.0f);
    }

    public void setTutorial(boolean tutorial) {
        isTutorial = tutorial;
        minigameState.setNoCatchDrain(tutorial);
        for (FishingTarget target : minigameState.getTargets()) {
            target.setNoCatchDrain(tutorial);
        }
    }

    public boolean isTutorial() { return isTutorial; }

    public void setPaused(boolean paused) {
        minigameState.setPaused(paused);
    }

    public void setInputForceMultiplier(float multiplier) {
        this.inputForceMultiplier = multiplier;
    }

    /** Called once at minigame start with the rod's currently equipped charm effect, if any. */
    public void setEquippedCharmEffect(@Nullable CharmEffect charmEffect) {
        this.equippedCharmEffect = charmEffect;
    }

    /** Called once at minigame start with the server-computed top-weighted fish (highest first), if any charm reveals them. */
    public void setTopWeightedFishPreviews(@Nullable List<ItemStack> stacks) {
        this.topWeightedFishPreviews = (stacks == null) ? List.of() : stacks;
    }

    /** Called once at minigame start with the species the player had never caught when they cast. */
    public void setUndiscoveredSpecies(@Nullable Set<Identifier> species) {
        this.undiscoveredSpecies = (species == null) ? Set.of() : Set.copyOf(species);
    }

    /** Called once at minigame start with the rod's currently equipped bait/hook/charm itemstacks, if any. */
    public void setEquippedGearStacks(@Nullable ItemStack bait, @Nullable ItemStack hook, @Nullable ItemStack charm) {
        this.equippedBaitStack = (bait == null || bait.isEmpty()) ? null : bait;
        this.equippedHookStack = (hook == null || hook.isEmpty()) ? null : hook;
        this.equippedCharmStack = (charm == null || charm.isEmpty()) ? null : charm;
    }

    /**
     * Called once at minigame start with the server-resolved zone(s) for the current cast
     * location. Copied into an EnumSet so the icon stack renders in a stable, deterministic order
     * (natural Zone declaration order) regardless of the incoming set's iteration order.
     */
    public void setCurrentZones(@Nullable Set<FishProfile.Zone> zones) {
        this.currentZones = (zones == null || zones.isEmpty()) ? null : EnumSet.copyOf(zones);
    }

    /**
     * Applies an upward impulse to the bobber (player interaction)
     * Does nothing if the minigame is hiding or in intro animation.
     */
    public void applyPlayerImpulse() {
        // Don't apply impulse during intro or hide animations
        if (isIntro || isHiding) {
            return;
        }
        minigameState.applyImpulse(FishingMinigameState.IMPULSE_STRENGTH * inputForceMultiplier);
    }

    /**
     * Gets the list of caught target indices
     * @return List of indices of targets that were caught
     */
    public List<Integer> getCaughtTargetIndices() {
        return new ArrayList<>(caughtTargetIndices);
    }

    /**
     * Gets the current minigame state
     * @return The fishing minigame state object
     */
    public FishingMinigameState getMinigameState() {
        return minigameState;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, float partialTick) {
        // Compute delta time in game-tick units (1.0 = 50 ms tick; ~0.33 at 60 fps).
        // Using tickCount + partialTick as an absolute clock avoids any dependency on
        // wall-clock timing and stays perfectly in sync with the game's own time source.
        float absoluteTime = tickCount + partialTick;
        if (lastRenderTime < 0f) lastRenderTime = absoluteTime;
        float deltaTime = Math.min(absoluteTime - lastRenderTime, 3.0f); // clamp against lag spikes
        lastRenderTime = absoluteTime;

        // The celebration runs on real elapsed time rather than the 20 Hz tick, so it's advanced
        // here. timeScale is what it hands back to the rest of the minigame: 0 freezes it outright,
        // fractions put it in slow motion.
        float timeScale = 1f;
        if (celebration != null) {
            celebration.advance(deltaTime);
            timeScale = celebration.getTimeScale();
        }

        // Per-frame input and physics — skip during intro / hide so the bobber is stable
        if (!isIntro && !isHiding) {
            // Poll both the dedicated keybind and vanilla's "use item" mapping (default: right
            // mouse button) every frame so right-click produces the same smooth, frame-rate
            // independent hold force as the keybind instead of vanilla's bursty click-repeat taps.
            boolean isImpulseDown = (FishtasticKeyBinds.fishingMinigameImpulse != null
                    && FishtasticKeyBinds.fishingMinigameImpulse.isDown())
                    || minecraft.options.keyUse.isDown();
            if (celebration != null) {
                // During a celebration the same input skips it instead of driving the bobber, so
                // the sequence can always be dismissed by a player who just wants to keep fishing.
                if (isImpulseDown && !wasImpulseKeyDown) {
                    celebration.skipToSettle();
                }
            } else if (isImpulseDown) {
                // Apply a small continuous force each frame, scaled by deltaTime so the
                // accumulated velocity per second is frame-rate independent.
                minigameState.applyImpulse(HOLD_IMPULSE_STRENGTH * inputForceMultiplier * deltaTime);
                if (!wasImpulseKeyDown) {
                    // Fire the tutorial callback only on the initial press, not every frame.
                    TutorialClientHandler.onMinigameImpulse();
                }
            }
            wasImpulseKeyDown = isImpulseDown;

            minigameState.updatePhysics(deltaTime * timeScale);
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // Calculate progress (0.0 to 1.0)
        float progress = absoluteTime / 600f; // Generic progress for any animations

        // Center of the screen
        int x = screenWidth / 2;
        int y = screenHeight / 2;

        render(minecraft, guiGraphics, partialTick, progress, x, y, screenWidth, screenHeight);
//        renderRewardsDisplay(guiGraphics, x, y, screenWidth, screenHeight);
    }

    private void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, float partialTick, float progress, int x, int y, int screenWidth, int screenHeight) {
        // ----- Render Fishing Bar + Bobber -----
        guiGraphics.pose().pushMatrix();
        // Celebration shake, applied in screen pixels before the bar's own translate + giant scale
        // so the whole bar assembly (bar, bobber, targets, sparkles) moves as one piece.
        if (celebration != null) {
            guiGraphics.pose().translate(celebration.getShakeX(), celebration.getShakeY());
        }
        renderFishingBar(minecraft, guiGraphics, partialTick, progress, x, y, screenHeight);
        renderTargets(guiGraphics, partialTick);
        renderSparkles(guiGraphics, partialTick);
        guiGraphics.pose().popMatrix();

        // Rendered in a fresh screen-pixel matrix (not nested under the bar's giant scale above)
        // so their size is a plain, fixed pixel footprint regardless of screen height. x/y here
        // are the bar's resting (un-animated) anchor — the side panels slide independently of
        // the bar's own intro/hide motion, not relative to its momentarily-offset position.
        renderAlmanacPreview(guiGraphics, partialTick, screenWidth, screenHeight);
        renderEquippedGear(guiGraphics, partialTick, x, y, screenHeight);
        renderBaitDepletedAnimation(guiGraphics, partialTick, x, y, screenHeight);
        renderZoneIcons(guiGraphics, partialTick, x, y, screenHeight);

        // Last, so the hero item and the flash sit on top of every other layer.
        renderCelebration(guiGraphics, screenWidth, screenHeight);
    }

    /**
     * Draws the catch celebration's screen flash and hero item, on top of everything else.
     *
     * <p>The hero goes through the normal item path rather than a direct texture blit, which is
     * what lets the existing quality outline shaders (see {@code gui_item_outline_legendary.fsh})
     * decorate it for free — the stack still carries its {@code FISH_QUALITY} component. The cost
     * is that the item rasterizes into a {@code 16 * guiScale} atlas slot regardless of how large
     * it's drawn (see {@link #renderItem}), so a heavily scaled-up fish is a chunky upscale rather
     * than a crisp one. That reads as deliberate at Minecraft's art scale; if it ever doesn't, the
     * fix is to lower {@code CatchCelebration}'s peak scale rather than to leave the item path.
     */
    private void renderCelebration(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight) {
        if (celebration == null) return;

        float flashAlpha = celebration.getFlashAlpha();
        if (flashAlpha > 0.01f) {
            int alpha = (int) (MathUtil.clamp(flashAlpha, 0f, 1f) * 255f);
            guiGraphics.fill(0, 0, screenWidth, screenHeight, (alpha << 24) | 0x00FFFFFF);
        }

        ItemStack hero = celebration.getHeroStack();
        if (hero.isEmpty()) return;

        // A normal reward item renders at (2/16) of the bar's 2*screenHeight/3 scale; the hero's
        // scale is expressed as a multiple of exactly that, so it stays proportional at any window
        // size the same way every other element of the bar does.
        float baseItemPx = (2f / 16f) * (2 * screenHeight / 3f);
        float heroPx = baseItemPx * celebration.getHeroScale();

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        guiGraphics.pose().pushMatrix();
        // Shaken along with the bar, so the held silhouette visibly strains during the suspense
        // build rather than sitting perfectly still while everything behind it rattles.
        guiGraphics.pose().translate(
                screenWidth / 2f + celebration.getHeroOffsetX() * screenHeight + celebration.getShakeX(),
                screenHeight / 2f + celebration.getHeroOffsetY() * screenHeight + celebration.getShakeY());
        guiGraphics.pose().rotate((float) Math.toRadians(celebration.getHeroRotation()));
        // Horizontal squeeze only — this is the Y-axis turn, faked the same way the fail animation
        // fakes its spin (see renderTargets), since GUI items have no real third axis.
        guiGraphics.pose().scale(heroPx * celebration.getHeroFlipScaleX(), heroPx);
        if (celebration.isSilhouetted()) {
            FishtasticGlintState.SILHOUETTE_REQUESTED.set(Boolean.TRUE);
        }
        try {
            extension.fishtastic$renderItem(hero, 0, 0);
        } finally {
            FishtasticGlintState.SILHOUETTE_REQUESTED.remove();
        }
        guiGraphics.pose().popMatrix();

        // Fires once, the frame the reveal starts — the burst is the punctuation on the reveal, so
        // it can't be spawned back when the catch actually happened.
        if (celebration.consumeSparkleBurst()) {
            spawnSparkleBurst(celebration.getSparkleCount(),
                    -celebration.getHeroOffsetY() / BAR_SPACE_TO_SCREEN_FRACTION);
        }
    }

    /**
     * Fraction in [0,1] of how far a side panel (as opposed to the bar itself, which has its own
     * independent slide) is displaced off-screen along its slide axis: 0 = fully at rest/visible,
     * 1 = fully off-screen. Shared by intro (entering) and hide (exiting) so both directions ease
     * with the same curves the bar's own animation uses, just applied to a different axis/edge
     * per panel (see {@link #renderAlmanacPreview} and {@link #renderEquippedGear}).
     *
     * @param delayTicks how long (in ticks) this particular item waits before starting its own
     *                    intro/hide, letting a group of items (e.g. the hook/charm stack) cascade
     *                    in one after another instead of moving as a single rigid block.
     */
    private float sidePanelDisplacement(float partialTick, float delayTicks) {
        if (isIntro) {
            // Each item gets the full INTRO_ANIMATION_DURATION regardless of its stagger delay,
            // so items naturally cascade — earlier items arrive sooner, later items arrive later.
            // No duration compression so they don't race to finish simultaneously.
            float elapsed = Math.max(0f, introAnimationTick + partialTick - delayTicks);
            float t = Math.min(1f, elapsed / INTRO_ANIMATION_DURATION);
            float eased = 1f - (1f - t) * (1f - t); // ease-out, matches the bar's own intro
            return 1f - eased;
        }
        // After the bar's intro ends, introAnimationTick is frozen but delayed items may still
        // be mid-slide. Continue using tickCount (which keeps incrementing) so they finish at
        // their own pace instead of snapping to rest.
        if (!isHiding) {
            float elapsed = Math.max(0f, tickCount + partialTick - delayTicks);
            if (elapsed > 0 && elapsed < INTRO_ANIMATION_DURATION) {
                float t = Math.min(1f, elapsed / INTRO_ANIMATION_DURATION);
                float eased = 1f - (1f - t) * (1f - t);
                return 1f - eased;
            }
            if (elapsed <= 0) return 1f; // hasn't started yet — still off-screen
        }
        if (isHiding) {
            // Hide animation has no hard cutoff — hideAnimationTick can run past the duration
            // (isActive() governs removal, not a boolean flip), so the original uniform
            // HIDE_ANIMATION_DURATION without duration compression works correctly here.
            float elapsed = Math.max(0f, hideAnimationTick + partialTick - delayTicks);
            float t = Math.min(1f, elapsed / HIDE_ANIMATION_DURATION);
            return t * t; // ease-in, matches the bar's own hide
        }
        return 0f;
    }

    /**
     * Renders the Angler's Almanac's top-weighted-fish preview off to the right of the minigame
     * bar, as a vertical stack of plain itemstack icons (highest-weighted first) — each
     * silhouetted the same way the fish encyclopedia silhouettes never-caught species, via the
     * {@link FishtasticGlintState#SILHOUETTE_REQUESTED} thread-local. The stack is centered on
     * the bar's midline and cascades in/out from the right edge of the screen, top entry leading.
     */
    private void renderAlmanacPreview(GuiGraphicsExtractor guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (topWeightedFishPreviews.isEmpty()) return;

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        float stackGap = SIDE_PANEL_ICON_SIZE + ALMANAC_ICON_GAP;
        float slotYTop = screenHeight / 2f - (topWeightedFishPreviews.size() - 1) * stackGap / 2f;

        for (int i = 0; i < topWeightedFishPreviews.size(); i++) {
            ItemStack stack = topWeightedFishPreviews.get(i);
            float slideX = sidePanelDisplacement(partialTick, GEAR_STAGGER_DELAY_TICKS * i) * screenWidth;
            boolean discovered = isFishDiscovered(stack);

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(screenWidth - SIDE_PANEL_MARGIN + slideX, slotYTop + stackGap * i);
            guiGraphics.pose().scale(SIDE_PANEL_ICON_SIZE, SIDE_PANEL_ICON_SIZE);
            if (!discovered) FishtasticGlintState.SILHOUETTE_REQUESTED.set(Boolean.TRUE);
            try {
                extension.fishtastic$renderItem(stack, 0, 0);
            } finally {
                FishtasticGlintState.SILHOUETTE_REQUESTED.remove();
            }
            guiGraphics.pose().popMatrix();
        }
    }

    /**
     * Read from the server-sent set rather than {@code FishEncyclopediaClientCache}, which this
     * previously consulted. That cache is only filled while the encyclopedia screen is open, so
     * during a cast it reported every species as never-caught and silhouetted the entire preview.
     */
    private boolean isFishDiscovered(ItemStack stack) {
        return !undiscoveredSpecies.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Renders the equipped bait (top), hook (middle), and charm (bottom) as a small stacked
     * column just outside the fishing bar's top-left corner. Plain itemstack icons, no
     * silhouette — unlike fish species, gear isn't something the player "discovers". Slides
     * in/out from the top edge of the screen, independent of (and opposite to) the bar's own
     * bottom-anchored slide, so the stack reads as a distinct layer: bar = the game itself, gear
     * readout = a HUD overlay on top of it.
     */
    private void renderEquippedGear(GuiGraphicsExtractor guiGraphics, float partialTick, int x, int y, int screenHeight) {
        if (equippedBaitStack == null && equippedHookStack == null && equippedCharmStack == null) return;

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;

        Vector2f barTopLeft = barContentTopLeft(x, y, screenHeight);
        float slotX = barTopLeft.x() - GEAR_PANEL_GAP - GEAR_ICON_SIZE / 2f;
        float slotYTop = barTopLeft.y() + GEAR_ICON_SIZE / 2f;
        float stackGap = GEAR_ICON_SIZE + GEAR_ICON_GAP;

        // Intro: bottom item enters first, cascading upward — lowest visible item
        // leads, each subsequent item trails by GEAR_STAGGER_DELAY_TICKS.
        // Hide: the same delay order applies (bottom exits first, top last), which
        // reads as the column peeling away from the bar.
        if (equippedBaitStack != null) {
            float slideY = -sidePanelDisplacement(partialTick, GEAR_STAGGER_DELAY_TICKS * 2f) * screenHeight;
            renderGearIcon(extension, guiGraphics, equippedBaitStack, slotX, slotYTop + slideY);
        }
        if (equippedHookStack != null) {
            float slideY = -sidePanelDisplacement(partialTick, GEAR_STAGGER_DELAY_TICKS) * screenHeight;
            renderGearIcon(extension, guiGraphics, equippedHookStack, slotX, slotYTop + stackGap + slideY);
        }
        if (equippedCharmStack != null) {
            float slideY = -sidePanelDisplacement(partialTick, 0f) * screenHeight;
            renderGearIcon(extension, guiGraphics, equippedCharmStack, slotX, slotYTop + stackGap * 2f + slideY);
        }
    }

    /**
     * Renders the equipped bait icon's pop-off, triggered the moment a catch is about to empty its
     * stack (see the {@code baitPopTriggered} check in {@link #tick()}). Reuses the same
     * {@link PhysicsSimulation} the caught fish's reward items animate with, so running out of bait
     * reads as the same kind of "spent" event rather than the gear icon just disappearing. That
     * simulation's units are tuned for the bar's own {@code 2 * screenHeight / 3} render scale (see
     * {@link #renderFishingBar}); this panel renders in raw screen pixels like the rest of the gear
     * column, so the interpolated offset is remapped through that same scale before being applied —
     * then further damped by {@link #BAIT_POP_SPEED_SCALE} so the small gear icon drifts off gently
     * rather than launching as far/fast as a full-size reward item would.
     */
    private void renderBaitDepletedAnimation(GuiGraphicsExtractor guiGraphics, float partialTick, int x, int y, int screenHeight) {
        if (baitDepletedSimulation == null) return;

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;

        Vector2f barTopLeft = barContentTopLeft(x, y, screenHeight);
        float slotX = barTopLeft.x() - GEAR_PANEL_GAP - GEAR_ICON_SIZE / 2f;
        float slotYTop = barTopLeft.y() + GEAR_ICON_SIZE / 2f;

        float physScale = 2 * screenHeight / 3f * BAIT_POP_SPEED_SCALE;
        Vector2f physPos = baitDepletedSimulation.getInterpolatedPosition(partialTick);
        Vector3f physRot = baitDepletedSimulation.getInterpolatedRotation(partialTick);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(slotX + physPos.x() * physScale, slotYTop - physPos.y() * physScale);
        guiGraphics.pose().rotate((float) Math.toRadians(physRot.z()));
        guiGraphics.pose().scale(GEAR_ICON_SIZE, GEAR_ICON_SIZE);
        extension.fishtastic$renderItem(baitDepletedSimulation.getItemStack(), 0, 0);
        guiGraphics.pose().popMatrix();
    }

    /**
     * Renders the current cast location's zone(s) as a small icon stack just outside the fishing
     * bar's top-right corner — the mirror image of {@link #renderEquippedGear} on the left. Same
     * slide-in/out treatment as the gear column but sliding from the top edge on the opposite side,
     * so the two readouts book-end the bar.
     */
    private void renderZoneIcons(GuiGraphicsExtractor guiGraphics, float partialTick, int x, int y, int screenHeight) {
        if (currentZones == null) return;

        Vector2f barTopRight = barContentTopRight(x, y, screenHeight);
        float slotX = barTopRight.x() + GEAR_PANEL_GAP + ZONE_ICON_SIZE / 2f;
        float slotYTop = barTopRight.y() + ZONE_ICON_SIZE / 2f;
        float stackGap = ZONE_ICON_SIZE + GEAR_ICON_GAP;

        int index = 0;
        for (FishProfile.Zone zone : currentZones) {
            Identifier texture = ZoneIconTextures.get(zone);
            if (texture == null) continue;

            float slideY = -sidePanelDisplacement(partialTick, GEAR_STAGGER_DELAY_TICKS * index) * screenHeight;
            renderZoneIcon(guiGraphics, texture, slotX, slotYTop + stackGap * index + slideY);
            index++;
        }
    }

    /**
     * Draws one zone icon centered at ({@code x}, {@code y}) as a direct texture blit plus a black
     * outline pass. Blitted rather than item-rendered for the reason given on {@link #renderItem}.
     */
    private static void renderZoneIcon(GuiGraphicsExtractor guiGraphics, Identifier texture, float x, float y) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x, y);

        // blit() takes integer coords, so work in source texels and scale them down to size.
        float texelScale = ZONE_ICON_SIZE / (float) ZONE_ICON_TEXTURE_PX;
        guiGraphics.pose().scale(texelScale, texelScale);
        int half = ZONE_ICON_TEXTURE_PX / 2;

        // Outline first, icon over it — matches the item path's submission order.
        guiGraphics.blit(FishtasticTextureOutlineEffect.getOrCreatePipeline(), texture,
                -half, -half, 0f, 0f,
                ZONE_ICON_TEXTURE_PX, ZONE_ICON_TEXTURE_PX, ZONE_ICON_TEXTURE_PX, ZONE_ICON_TEXTURE_PX);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                -half, -half, 0f, 0f,
                ZONE_ICON_TEXTURE_PX, ZONE_ICON_TEXTURE_PX, ZONE_ICON_TEXTURE_PX, ZONE_ICON_TEXTURE_PX);

        guiGraphics.pose().popMatrix();
    }

    /** Mirror of {@link #barContentTopLeft} — top-right corner of the bar's visible content. */
    private static Vector2f barContentTopRight(int x, int y, int screenHeight) {
        float scale = 2 * screenHeight / 3f;
        GuiTextureItem bar = LAYOUT.bar();
        float barWidthPx = (bar.uw() / (float) bar.texWidth()) * scale;
        float barHeightPx = (bar.vh() / (float) bar.texHeight()) * scale;
        return new Vector2f(x + barWidthPx / 2f, y - barHeightPx / 2f);
    }

    /**
     * Renders one gear icon with a solid black edge outline, via the same
     * {@code FishtasticGlintState} thread-local-flag → mixin pipeline the fish encyclopedia's
     * silhouette effect uses (see {@link #isFishDiscovered}) — just requesting an outline layer
     * instead of a fill, so the small icon reads clearly against a busy background.
     */
    private static void renderGearIcon(IGuiGraphicsExtension extension, GuiGraphicsExtractor guiGraphics, ItemStack stack, float x, float y) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x, y);
        guiGraphics.pose().scale(GEAR_ICON_SIZE, GEAR_ICON_SIZE);
        FishtasticGlintState.BLACK_OUTLINE_REQUESTED.set(Boolean.TRUE);
        try {
            extension.fishtastic$renderItem(stack, 0, 0);
        } finally {
            FishtasticGlintState.BLACK_OUTLINE_REQUESTED.remove();
        }
        guiGraphics.pose().popMatrix();
    }

    /**
     * Top-left corner of the fishing bar's visible content (not its full padded square footprint)
     * at rest, in screen pixels. The bar's {@link GuiTextureItem} content is rendered centered
     * exactly at ({@code x}, {@code y}) — see {@link #renderItem} — sized to the fraction of its
     * texture the content actually occupies (uw/texWidth wide, vh/texHeight tall) once scaled up
     * by the bar's own {@code 2 * screenHeight / 3} render scale.
     */
    private static Vector2f barContentTopLeft(int x, int y, int screenHeight) {
        float scale = 2 * screenHeight / 3f;
        GuiTextureItem bar = LAYOUT.bar();
        float barWidthPx = (bar.uw() / (float) bar.texWidth()) * scale;
        float barHeightPx = (bar.vh() / (float) bar.texHeight()) * scale;
        return new Vector2f(x - barWidthPx / 2f, y - barHeightPx / 2f);
    }

    private void renderSparkles(GuiGraphicsExtractor guiGraphics, float partialTick) {
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        final float sparkleScale = 1f / 16f;

        for (SparkleBurst burst : sparkleBursts) {
            for (SparkleParticle sparkle : burst.particles()) {
                guiGraphics.pose().pushMatrix();
                Vector2f pos = sparkle.getInterpolatedPosition(partialTick);
                float rotZ = sparkle.getInterpolatedRotationZ(partialTick);
                float scale = sparkleScale * (1f - sparkle.getLifetimeProgress());
                guiGraphics.pose().translate(pos.x(), -burst.targetYOffset() - pos.y());
                guiGraphics.pose().rotate((float) Math.toRadians(rotZ));
                guiGraphics.pose().scale(scale, scale);
                extension.fishtastic$renderItem(sparkle.getItemStack(), 0, 0);
                guiGraphics.pose().popMatrix();
            }
        }
    }

    private void renderFishingBar(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, float partialTick, float progress, int x, int y, int screenHeight) {
        float angle = 0f; // Can use to add a slight rotation effect if desired

        // Calculate vertical offset for intro and hide animations
        float verticalOffset = 0f;

        // Intro animation: slide in from bottom
        if (isIntro) {
            float introProgress = (introAnimationTick + partialTick) / INTRO_ANIMATION_DURATION;
            introProgress = Math.min(1.0f, introProgress); // Clamp to 1.0
            // Ease-out function for smooth deceleration
            introProgress = 1.0f - (1.0f - introProgress) * (1.0f - introProgress);
            verticalOffset = (1.0f - introProgress) * screenHeight; // Slide up from positive (bottom)
        }

        // Hide animation: slide out bottom
        if (isHiding) {
            float hideProgress = (hideAnimationTick + partialTick) / HIDE_ANIMATION_DURATION;
            hideProgress = Math.min(1.0f, hideProgress); // Clamp to 1.0
            // Ease-in function for smooth acceleration
            hideProgress = hideProgress * hideProgress;
            verticalOffset = hideProgress * screenHeight; // Slide down by screen height
        }

        guiGraphics.pose().translate(x, y + verticalOffset);

        float scale = 2 * screenHeight / 3f;
        guiGraphics.pose().scale(scale, scale);

        renderItem(LAYOUT.bar(), guiGraphics, minecraft, angle, 0);

        guiGraphics.pose().pushMatrix();
        float normalizedBobberPosition = minigameState.getBobberPosition();
        float yOffset = normalizedBobberPosition * LAYOUT.bobberMaxYOffset();
        guiGraphics.pose().translate(0, -yOffset);
        renderItem(LAYOUT.bobber(), guiGraphics, minecraft, angle, 1);
        guiGraphics.pose().popMatrix();
    }

    private void renderTargets(GuiGraphicsExtractor guiGraphics, float partialTick) {
        // Render all targets from the minigame state
        final float itemMaxYOffset = LAYOUT.itemMaxYOffset();
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;

        // Calculate bobber bounds for shake effect
        final float normalizedBobberMinY = minigameState.getBobberPosition();
        final float normalizedBobberMaxY = normalizedBobberMinY + minigameState.getBobberSize();

        // ----- Render Targets -----
        int zOffset = 2;
        for (FishingTarget target : minigameState.getTargets()) {
            guiGraphics.pose().pushMatrix();

            FishingTarget.TargetState targetState = target.getState();

            // Get the display item - use generic fish for FishtasticFish items during active/fail states
            boolean showRarityOutline = equippedCharmEffect != null && equippedCharmEffect.showRarityOutline();
            ItemStack displayItem = target.getDisplayItemStack(showRarityOutline);

            if (targetState == FishingTarget.TargetState.ACTIVE) {
                // Existing rendering logic for active targets
                // Get interpolated position for smooth rendering
                float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f; // -0.5 to 0.5 for correct position
                float targetYOffset = targetPosition * itemMaxYOffset;

                float catchProgress = target.getCatchProgress();

                // Add shake effect only if target is currently being caught (within bobber)
                float targetRawPosition = target.getInterpolatedPosition(partialTick);
                boolean isTargetWithinBobber = targetRawPosition >= normalizedBobberMinY && targetRawPosition <= normalizedBobberMaxY;
                float shakeAngle = 0f;
                if (isTargetWithinBobber) {
                    float shakeOffset = target.getShakeOffset(partialTick);
                    targetYOffset += shakeOffset;
                    float baseFreq = 0.05f;
                    final float freqMultiplier = 0.2f;
                    final float baseAmplitude = 10f;
                    shakeAngle = target.getShakeAngle(partialTick, baseFreq, freqMultiplier, baseAmplitude);
                }

                // Calculate scale based on catch progress
                float scaleMultiplier = 0.5f + (catchProgress * 0.5f);
                final float itemScale = (2 / 16f) * scaleMultiplier;

                float prog = Math.max(0, (0.5f - catchProgress) * 2f);
                Vector3f color = Utility.interpolateColor(
                        new Vector3f(1, 1, 1), // White at progress 0
                        new Vector3f(1, 0.25f, 0.25f), // Red at progress 1
                        prog
                );

                // Squash-and-stretch telegraph cues
                float dartCoil       = target.getDartCoilProgress();
                float dartBurst      = target.getDartBurstProgress();
                float lungeTelegraph = target.getLungeTelegraphProgress();
                float squashX, squashY;
                if (dartCoil > 0f) {
                    // DART coil: widen + flatten as tension builds
                    squashX = 1f + dartCoil * 0.35f;
                    squashY = 1f - dartCoil * 0.35f;
                } else if (dartBurst > 0f && dartBurst < 0.3f) {
                    // DART release: brief stretch in direction of travel
                    float t = dartBurst / 0.3f;
                    squashX = 1f - t * 0.30f;
                    squashY = 1f + t * 0.40f;
                } else if (lungeTelegraph > 0f) {
                    // LUNGE coil: compress horizontally, extend vertically
                    squashX = 1f - lungeTelegraph * 0.30f;
                    squashY = 1f + lungeTelegraph * 0.40f;
                } else {
                    squashX = 1f;
                    squashY = 1f;
                }

                guiGraphics.pose().translate(0, -targetYOffset);
                guiGraphics.pose().rotate((float) Math.toRadians(shakeAngle));
                guiGraphics.pose().scale(itemScale * squashX, itemScale * squashY);
                // setColor removed in 26.1 - render without tinting
                extension.fishtastic$renderItem(displayItem, 0, 0);

            } else if (targetState == FishingTarget.TargetState.ANIMATING_SUCCESS) {
                // Collection animation: physics-based movement with rotation on all axes
                // Render each reward item with its own physics simulation
                float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f;
                float targetYOffset = targetPosition * itemMaxYOffset;

                final float itemScale = (2 / 16f);

                for (PhysicsSimulation simulation : target.getPhysicsSimulations()) {
                    guiGraphics.pose().pushMatrix();

                    Vector2f successPhysSim = simulation.getInterpolatedPosition(partialTick);
                    Vector3f successRotation = simulation.getInterpolatedRotation(partialTick);

                    guiGraphics.pose().translate(successPhysSim.x(), -targetYOffset - successPhysSim.y());
                    // In 2D, only Z-axis rotation is meaningful
                    guiGraphics.pose().rotate((float) Math.toRadians(successRotation.z));
                    guiGraphics.pose().scale(itemScale, itemScale);
                    extension.fishtastic$renderItem(simulation.getItemStack(), 0, 0);

                    guiGraphics.pose().popMatrix();
                    zOffset++; // Each item gets its own z-level to prevent z-fighting
                }

            } else if (targetState == FishingTarget.TargetState.ANIMATING_FAIL) {
                // Fail animation: spin on Y-axis and shrink
                float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f;
                float targetYOffset = targetPosition * itemMaxYOffset;

                float spinAngle = target.getFailSpinAngle(partialTick);
                float collectScale = target.getCollectionScale(partialTick);
                // Match the active-state scale (0.5 + catchProgress*0.5) so there's no pop on transition.
                // catchProgress is 0 at failure time, so this is always 0.5× — matching the smallest active size.
                float scaleMultiplier = 0.5f + (target.getCatchProgress() * 0.5f);
                final float itemScale = (2 / 16f) * scaleMultiplier * collectScale;

                guiGraphics.pose().translate(0, -targetYOffset);
                // Y-axis spin doesn't apply in 2D - use scale-x for a flip effect
                float flipScale = (float) Math.cos(Math.toRadians(spinAngle));
                guiGraphics.pose().scale(itemScale * flipScale, itemScale);
                extension.fishtastic$renderItem(displayItem, 0, 0);
            }

            guiGraphics.pose().popMatrix();
            zOffset++; // Increment z-offset for each target so they don't z-fight
        }
    }

    private static void renderRewardsDisplay(GuiGraphicsExtractor guiGraphics, int x, int y, int screenWidth, int screenHeight) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x - (screenHeight / 3f), y);

        float rewardDisplayScale = screenHeight / 3f;
        guiGraphics.pose().scale(rewardDisplayScale, rewardDisplayScale);

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        extension.fishtastic$renderItem(new ItemStack(Blocks.BRICKS), 0, 0);
        guiGraphics.pose().popMatrix();
    }

    // -------------------------------------------------------------------------
    // Sprite layout — single source of truth for all sizing constants.
    // To resize or reshape the bar/bobber textures, only edit LAYOUT below.
    // -------------------------------------------------------------------------

    /**
     * Texture coordinates and texture location for one sprite.
     *
     * @param u        Left edge of the sprite content within the texture (px)
     * @param v        Top edge of the sprite content within the texture (px)
     * @param uw       Width of the sprite content (px)
     * @param vh       Height of the sprite content (px)
     * @param texWidth  Full texture width (px)
     * @param texHeight Full texture height (px)
     * @param texture   Standalone texture to blit — a full resource path, not an item-model reference
     */
    public record GuiTextureItem(int u, int v, int uw, int vh, int texWidth, int texHeight, Identifier texture, Vector2f localPivot) {
        public GuiTextureItem(int u, int v, int uw, int vh, int texWidth, int texHeight, Identifier texture) {
            this(u, v, uw, vh, texWidth, texHeight, texture, calculateLocalPivot(u, v, uw, vh, texWidth, texHeight));
        }

        private static Vector2f calculateLocalPivot(int u, int v, int uw, int vh, int texWidth, int texHeight) {
            float x = (u + uw / 2f) / texWidth - 0.5f;
            float y = (v + vh / 2f) / texHeight - 0.5f;
            return new Vector2f(x, y);
        }
    }

    /**
     * All sizing parameters for the fishing bar overlay in one place.
     *
     * @param bar           Bar sprite definition
     * @param bobber        Bobber sprite definition
     * @param travelZonePx  Pixel height of the bobber's playable travel area within the bar
     *                      (texture height minus top + bottom decorative margins)
     * @param bobberHeightPx Pixel height of the bobber sprite within that travel zone
     * @param targetZonePx  Pixel height of the zone target icons may appear in
     *                      (typically slightly tighter than travelZonePx)
     */
    public record FishingBarLayout(
            GuiTextureItem bar,
            GuiTextureItem bobber,
            int travelZonePx,
            int bobberHeightPx,
            int targetZonePx
    ) {
        /** Fraction of bar texture height the bobber can travel — passed to the renderer. */
        public float bobberMaxYOffset() { return (float) travelZonePx / bar.texHeight(); }

        /** Bobber size as a fraction of the travel zone — passed to FishingMinigameState. */
        public float bobberSize()       { return (float) bobberHeightPx / travelZonePx; }

        /** Fraction of bar texture height target icons may travel — passed to the renderer. */
        public float itemMaxYOffset()   { return (float) targetZonePx / bar.texHeight(); }
    }

    public static final FishingBarLayout LAYOUT = new FishingBarLayout(
            new GuiTextureItem(0, 0, 8, 32, 32, 32, Fishtastic.id("textures/item/fishing_bar.png")),
            new GuiTextureItem(0, 0, 8, 32, 32, 32, Fishtastic.id("textures/item/fishing_bobber.png")),
            28,  // travel zone: 32px texture minus ~2px margin at each end
            9,   // bobber height in pixels
            26   // target zone: slightly tighter margins than the bobber travel zone
    );

    /**
     * Draws one sprite as a direct texture blit, centered on the current pose origin and occupying a
     * 1×1 unit box, so callers set the on-screen size purely via their own pose scale.
     *
     * <p>Not an item render: {@code GuiGraphicsExtractor.item()} rasterizes into a
     * {@code 16 * guiScale} px atlas slot regardless of on-screen size, starving any 32px sprite
     * drawn larger than 16 GUI units. The gear icons stay on the item path — they render arbitrary
     * stacks, rely on {@link FishtasticGlintState}, and at 16 units the slot is big enough.
     */
    private static void renderItem(GuiTextureItem guiTextureItem, GuiGraphicsExtractor guiGraphics, Minecraft minecraft, float angle, int zOffset) {
        guiGraphics.pose().pushMatrix();

        Vector2f pivot = guiTextureItem.localPivot();
        guiGraphics.pose().rotate((float) Math.toRadians(angle)); // Rotate around sprite center
        guiGraphics.pose().translate(-pivot.x(), -pivot.y()); // Center sprite content at screen origin

        // blit() takes integer coords, so work in texel units and shrink one texel to 1/texWidth of
        // a unit — the full texture then spans the 1×1 box callers' pose scale already assumes.
        int texW = guiTextureItem.texWidth();
        int texH = guiTextureItem.texHeight();
        guiGraphics.pose().scale(1f / texW, 1f / texH);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, guiTextureItem.texture(),
                -texW / 2, -texH / 2, 0f, 0f, texW, texH, texW, texH);

        guiGraphics.pose().popMatrix();
    }
}
