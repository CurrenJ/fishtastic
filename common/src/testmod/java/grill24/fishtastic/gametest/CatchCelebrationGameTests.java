package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.util.CatchCelebration;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

/**
 * Game tests for the catch celebration timeline. All logic is pure — the class holds a clock and
 * derives everything analytically from it, so none of this needs a world, a screen, or a frame.
 */
public final class CatchCelebrationGameTests {

    private CatchCelebrationGameTests() {}

    private static final float EPSILON = 0.001f;

    private static ItemStack legendaryFish() {
        ItemStack stack = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(stack, FishQuality.Quality.LEGENDARY);
        return stack;
    }

    private static ItemStack commonFish() {
        ItemStack stack = new ItemStack(Items.SALMON);
        FishQualityHelper.setQuality(stack, FishQuality.Quality.COMMON);
        return stack;
    }

    private static CatchCelebration heroCelebration() {
        return new CatchCelebration(CatchCelebration.Tier.HERO, legendaryFish(), 0f, new Random(1234L));
    }

    /**
     * Mirrors {@code CatchCelebration}'s own private swap point: a quarter of the way through the
     * reveal turn, where the item is edge-on.
     */
    private static float revealSwapTime(CatchCelebration.Timings timings) {
        return timings.revealStart() + timings.reveal() * 0.25f;
    }

    /** Advances a celebration to an absolute point on its timeline in small, frame-sized steps. */
    private static void advanceTo(CatchCelebration celebration, float targetTime) {
        while (celebration.getTime() < targetTime - EPSILON) {
            celebration.advance(Math.min(0.25f, targetTime - celebration.getTime()));
        }
    }

    // -------------------------------------------------------------------------
    // Tier resolution
    // -------------------------------------------------------------------------

    /** A legendary reward earns the full hero sequence. */
    public static void legendaryResolvesToHeroTier(GameTestHelper helper) {
        CatchCelebration.Tier tier = CatchCelebration.resolveTier(List.of(legendaryFish()), stack -> false);
        helper.assertTrue(tier == CatchCelebration.Tier.HERO, "Legendary must resolve to HERO, got " + tier);
        helper.succeed();
    }

    /** An ordinary, already-discovered catch earns no celebration at all. */
    public static void ordinaryCatchResolvesToNone(GameTestHelper helper) {
        CatchCelebration.Tier tier = CatchCelebration.resolveTier(List.of(commonFish()), stack -> false);
        helper.assertTrue(tier == CatchCelebration.Tier.NONE, "Known common fish must resolve to NONE, got " + tier);
        helper.succeed();
    }

    /** A never-caught species earns the shorter discovery sequence. */
    public static void undiscoveredResolvesToDiscoveryTier(GameTestHelper helper) {
        CatchCelebration.Tier tier = CatchCelebration.resolveTier(List.of(commonFish()), stack -> true);
        helper.assertTrue(tier == CatchCelebration.Tier.DISCOVERY, "New species must resolve to DISCOVERY, got " + tier);
        helper.succeed();
    }

    /**
     * A first-ever legendary is both things at once. It must fire the hero sequence only — stacking
     * two celebrations on one catch would play the discovery jingle over the top of the hero one.
     */
    public static void legendaryOutranksDiscovery(GameTestHelper helper) {
        CatchCelebration.Tier tier = CatchCelebration.resolveTier(List.of(legendaryFish()), stack -> true);
        helper.assertTrue(tier == CatchCelebration.Tier.HERO,
            "Legendary must outrank discovery, got " + tier);
        helper.succeed();
    }

    /** An empty reward list can't be celebrated. */
    public static void emptyRewardsResolveToNone(GameTestHelper helper) {
        CatchCelebration.Tier tier = CatchCelebration.resolveTier(List.of(), stack -> true);
        helper.assertTrue(tier == CatchCelebration.Tier.NONE, "No rewards must resolve to NONE, got " + tier);
        helper.succeed();
    }

    /** With mixed rewards, the highest-quality stack is the one put on stage. */
    public static void heroStackPicksHighestQuality(GameTestHelper helper) {
        ItemStack legendary = legendaryFish();
        ItemStack hero = CatchCelebration.pickHeroStack(
            List.of(commonFish(), legendary), CatchCelebration.Tier.HERO, stack -> false);
        helper.assertTrue(hero.is(legendary.getItem()),
            "Hero stack must be the legendary reward, got " + hero.getItem());
        helper.succeed();
    }

    /** On a discovery, the new species is the one staged even if another reward outranks it. */
    public static void heroStackPicksNewSpeciesOnDiscovery(GameTestHelper helper) {
        ItemStack newSpecies = commonFish();
        ItemStack hero = CatchCelebration.pickHeroStack(
            List.of(new ItemStack(Items.TROPICAL_FISH), newSpecies),
            CatchCelebration.Tier.DISCOVERY,
            stack -> stack.is(newSpecies.getItem()));
        helper.assertTrue(hero.is(newSpecies.getItem()),
            "Discovery must stage the new species, got " + hero.getItem());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Timeline
    // -------------------------------------------------------------------------

    /** Each phase must begin exactly where the timings say it does. */
    public static void phaseBoundariesFollowTimings(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.HITSTOP,
            "Must open on HITSTOP, got " + celebration.getPhase());

        advanceTo(celebration, timings.launchStart());
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.LAUNCH,
            "Must be LAUNCH at t=" + timings.launchStart() + ", got " + celebration.getPhase());

        advanceTo(celebration, timings.suspenseStart());
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.SUSPENSE,
            "Must be SUSPENSE at t=" + timings.suspenseStart() + ", got " + celebration.getPhase());

        advanceTo(celebration, timings.revealStart());
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.REVEAL,
            "Must be REVEAL at t=" + timings.revealStart() + ", got " + celebration.getPhase());

        advanceTo(celebration, timings.hangStart());
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.HANG,
            "Must be HANG at t=" + timings.hangStart() + ", got " + celebration.getPhase());

        advanceTo(celebration, timings.settleStart());
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.SETTLE,
            "Must be SETTLE at t=" + timings.settleStart() + ", got " + celebration.getPhase());

        advanceTo(celebration, timings.total());
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.DONE,
            "Must be DONE at t=" + timings.total() + ", got " + celebration.getPhase());
        helper.assertTrue(celebration.isFinished(), "Must report finished at the end of the timeline");
        helper.succeed();
    }

    /** The clock never runs past the end of the timeline, however large a frame delta arrives. */
    public static void clockClampsAtTotal(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        celebration.advance(9999f);
        helper.assertTrue(celebration.getTime() == CatchCelebration.HERO_TIMINGS.total(),
            "Clock must clamp to total, got " + celebration.getTime());
        helper.assertTrue(celebration.isFinished(), "Must be finished after a huge delta");
        helper.succeed();
    }

    /**
     * Time scale: a dead stop on impact, slow motion through the middle, and fully handed back by
     * the end. A celebration that left the game in slow motion would break every following cast.
     */
    public static void timeScaleFreezesThenRampsBack(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(celebration.getTimeScale() == 0f,
            "Hitstop must fully freeze, got " + celebration.getTimeScale());

        advanceTo(celebration, timings.hangStart());
        helper.assertTrue(Math.abs(celebration.getTimeScale() - timings.holdScale()) < EPSILON,
            "Hang must hold the slow-motion rate, got " + celebration.getTimeScale());

        advanceTo(celebration, timings.total());
        helper.assertTrue(Math.abs(celebration.getTimeScale() - 1f) < EPSILON,
            "Time must be fully handed back by the end, got " + celebration.getTimeScale());
        helper.succeed();
    }

    /** The discovery variant is deliberately not slow motion once the item is up. */
    public static void discoveryHasNoSlowMotionHold(GameTestHelper helper) {
        CatchCelebration celebration =
            new CatchCelebration(CatchCelebration.Tier.DISCOVERY, commonFish(), 0f, new Random(1234L));

        advanceTo(celebration, CatchCelebration.DISCOVERY_TIMINGS.hangStart());
        helper.assertTrue(Math.abs(celebration.getTimeScale() - 1f) < EPSILON,
            "Discovery hang must run at normal speed, got " + celebration.getTimeScale());
        helper.succeed();
    }

    /**
     * The hero item grows to its peak, holds there, then shrinks back to ordinary reward size and
     * stays at it. It must never scale away to nothing — the item leaves because physics carries
     * it off screen, and a shrinking-to-zero exit would read as it evaporating instead.
     */
    public static void heroScaleGrowsToPeakThenReturnsToNormal(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(Math.abs(celebration.getHeroScale() - 1f) < EPSILON,
            "Must start at normal item size, got " + celebration.getHeroScale());

        advanceTo(celebration, timings.hangStart());
        helper.assertTrue(Math.abs(celebration.getHeroScale() - timings.peakScale()) < EPSILON,
            "Must reach peak scale by the hang, got " + celebration.getHeroScale());

        advanceTo(celebration, timings.settleStart() + timings.settle() * 0.6f);
        helper.assertTrue(Math.abs(celebration.getHeroScale() - 1f) < 0.05f,
            "Must be back to normal size partway through the wind-down, got " + celebration.getHeroScale());

        advanceTo(celebration, timings.total());
        helper.assertTrue(Math.abs(celebration.getHeroScale() - 1f) < EPSILON,
            "Must hold at normal size rather than vanishing, got " + celebration.getHeroScale());
        helper.succeed();
    }

    /**
     * The sequence ends as soon as the released item has flown off screen, rather than waiting out
     * the full settle window — that early exit is what lets the item leave under its own momentum
     * instead of being scaled or faded out.
     */
    public static void settleFinishesOnceItemLeavesScreen(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        advanceTo(celebration, timings.settleStart());
        helper.assertTrue(!celebration.isFinished(), "Must not be finished the moment it is released");

        // Well inside the settle window, the arc should already have carried it out of view.
        advanceTo(celebration, timings.total() - 1f);
        helper.assertTrue(celebration.isFinished(),
            "Must finish once off screen, still running at offsetY=" + celebration.getHeroOffsetY());
        helper.assertTrue(celebration.getHeroOffsetY() > 0.5f,
            "Must have fallen past the bottom edge, got " + celebration.getHeroOffsetY());
        helper.succeed();
    }

    /**
     * The wind-down releases the hero item into a physics simulation rather than retracing its
     * launch arc, so it tumbles off the way an ordinary reward does. Position must therefore keep
     * changing frame to frame once the settle begins, instead of being pinned to the hang point.
     */
    public static void settleReleasesIntoPhysics(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.hangStart());
        float hangY = celebration.getHeroOffsetY();

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.settleStart() + 4f);
        float earlyY = celebration.getHeroOffsetY();
        helper.assertTrue(earlyY != hangY,
            "Item must move once released into physics, still at " + hangY);

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.settleStart() + 12f);
        helper.assertTrue(celebration.getHeroOffsetY() != earlyY,
            "Item must keep moving under physics, stuck at " + earlyY);
        helper.succeed();
    }

    /**
     * Gravity is acting on the released item: its upward motion decelerates. Asserted as
     * deceleration rather than "ends up lower than it started", because the launch velocity is
     * randomised and the arc's apex can land past the end of the window — the item is often still
     * rising when the sequence finishes, which is fine, since the vanish is what ends it.
     */
    public static void settlePhysicsDeceleratesUnderGravity(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        advanceTo(celebration, timings.settleStart() + 2f);
        float a = celebration.getHeroOffsetY();
        advanceTo(celebration, timings.settleStart() + 6f);
        float b = celebration.getHeroOffsetY();
        advanceTo(celebration, timings.settleStart() + 10f);
        float c = celebration.getHeroOffsetY();

        // Screen Y grows downward, so rising is a decreasing Y. Each equal interval must climb
        // less than the one before it.
        float firstRise = a - b;
        float secondRise = b - c;
        helper.assertTrue(secondRise < firstRise,
            "Vertical motion must decelerate under gravity, rises were " + firstRise + " then " + secondRise);
        helper.succeed();
    }

    /** The tumble is physics-driven once released, not the gentle hang wobble. */
    public static void settleRotationComesFromPhysics(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.settleStart() + 8f);
        float first = celebration.getHeroRotation();

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.settleStart() + 12f);
        helper.assertTrue(celebration.getHeroRotation() != first,
            "Released item must keep tumbling, stuck at " + first);
        helper.succeed();
    }

    /**
     * The prize stays unreadable until the reveal — that withholding is the entire point of the
     * sequence, so it's worth a test that would catch someone "simplifying" it away.
     */
    public static void silhouetteDropsAtReveal(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(celebration.isSilhouetted(), "Must be silhouetted during hitstop");

        advanceTo(celebration, timings.launchStart());
        helper.assertTrue(celebration.isSilhouetted(), "Must still be silhouetted while rising");

        // The whole point of the suspense hold: at full size, unmistakably there, still unreadable.
        advanceTo(celebration, timings.suspenseStart());
        helper.assertTrue(celebration.isSilhouetted(), "Must still be silhouetted through the suspense hold");
        helper.assertTrue(Math.abs(celebration.getHeroScale() - timings.peakScale()) < EPSILON,
            "Suspense must hold at peak scale, got " + celebration.getHeroScale());

        // The turn has begun but the swap has not: still a silhouette, now spinning.
        advanceTo(celebration, timings.revealStart());
        helper.assertTrue(celebration.isSilhouetted(), "Must stay silhouetted as the turn begins");

        advanceTo(celebration, revealSwapTime(timings) - 0.5f);
        helper.assertTrue(celebration.isSilhouetted(), "Must stay silhouetted right up to the swap");

        advanceTo(celebration, revealSwapTime(timings));
        helper.assertTrue(!celebration.isSilhouetted(), "Must be revealed once the item is edge-on");
        helper.succeed();
    }

    /**
     * The silhouette is swapped for the real fish exactly when the item is edge-on to the camera,
     * where its horizontal scale passes through zero. That is what makes the substitution invisible
     * — a swap at any other angle would be a visible sprite change.
     */
    public static void revealSwapHappensWhileEdgeOn(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        advanceTo(celebration, timings.revealStart());
        helper.assertTrue(Math.abs(celebration.getHeroFlipScaleX() - 1f) < EPSILON,
            "Must enter the turn square to the camera, got " + celebration.getHeroFlipScaleX());

        advanceTo(celebration, revealSwapTime(timings));
        helper.assertTrue(Math.abs(celebration.getHeroFlipScaleX()) < 0.05f,
            "Must be edge-on at the swap, got " + celebration.getHeroFlipScaleX());
        helper.assertTrue(!celebration.isSilhouetted(), "Must have swapped to the real fish while edge-on");

        // Back half of the turn shows the sprite mirrored, as a flat object showing its reverse.
        advanceTo(celebration, timings.revealStart() + timings.reveal() * 0.5f);
        helper.assertTrue(celebration.getHeroFlipScaleX() < -0.9f,
            "Must be mirrored halfway through the turn, got " + celebration.getHeroFlipScaleX());

        advanceTo(celebration, timings.hangStart());
        helper.assertTrue(Math.abs(celebration.getHeroFlipScaleX() - 1f) < EPSILON,
            "Must leave the turn square to the camera, got " + celebration.getHeroFlipScaleX());
        helper.succeed();
    }

    /** The item is only ever squeezed during the turn — never before it, never after. */
    public static void heroIsUnsqueezedOutsideTheTurn(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(Math.abs(celebration.getHeroFlipScaleX() - 1f) < EPSILON, "No squeeze during hitstop");

        advanceTo(celebration, timings.suspenseStart());
        helper.assertTrue(Math.abs(celebration.getHeroFlipScaleX() - 1f) < EPSILON, "No squeeze during suspense");

        advanceTo(celebration, timings.settleStart());
        helper.assertTrue(Math.abs(celebration.getHeroFlipScaleX() - 1f) < EPSILON, "No squeeze once released");
        helper.succeed();
    }

    /** The sparkle burst is punctuation on the reveal: once, and not before it. */
    public static void sparkleBurstFiresOnceAtReveal(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(!celebration.consumeSparkleBurst(), "Must not burst before the reveal");

        advanceTo(celebration, timings.revealStart());
        helper.assertTrue(!celebration.consumeSparkleBurst(),
            "Must not burst as the turn begins — the fish is still hidden");

        advanceTo(celebration, revealSwapTime(timings));
        helper.assertTrue(celebration.consumeSparkleBurst(), "Must burst as the fish is revealed");
        helper.assertTrue(!celebration.consumeSparkleBurst(), "Must not burst a second time");
        helper.succeed();
    }

    /** Once the reveal has played, skipping jumps to the wind-down and still completes cleanly. */
    public static void skipJumpsToSettle(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.hangStart());
        celebration.skipToSettle();

        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.SETTLE,
            "Skip must land on SETTLE, got " + celebration.getPhase());

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.total());
        helper.assertTrue(celebration.isFinished(), "A skipped celebration must still finish");
        helper.assertTrue(Math.abs(celebration.getTimeScale() - 1f) < EPSILON,
            "A skipped celebration must still hand time back, got " + celebration.getTimeScale());
        helper.succeed();
    }

    /**
     * Skipping does nothing before the reveal. The impulse key that skips is the same key being
     * mashed to play the minigame, so an ungated skip would fire almost immediately on a real catch
     * and throw away the payoff the sequence exists to deliver.
     */
    public static void skipIgnoredBeforeReveal(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(!celebration.isSkippable(), "Must not be skippable during hitstop");
        celebration.skipToSettle();
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.HITSTOP,
            "Skip during hitstop must be ignored, got " + celebration.getPhase());

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.launchStart());
        helper.assertTrue(!celebration.isSkippable(), "Must not be skippable while rising");
        celebration.skipToSettle();
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.LAUNCH,
            "Skip during launch must be ignored, got " + celebration.getPhase());

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.suspenseStart());
        helper.assertTrue(!celebration.isSkippable(), "Must not be skippable during the suspense hold");
        celebration.skipToSettle();
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.SUSPENSE,
            "Skip during suspense must be ignored, got " + celebration.getPhase());

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.hangStart());
        helper.assertTrue(celebration.isSkippable(), "Must be skippable once the reveal has played");
        helper.succeed();
    }

    /** The sparkle burst fires on its own at the reveal even if the player skips right after it. */
    public static void skipAfterRevealKeepsBurstConsumed(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.hangStart());

        helper.assertTrue(celebration.consumeSparkleBurst(), "Burst must be pending at the hang");
        celebration.skipToSettle();
        helper.assertTrue(!celebration.consumeSparkleBurst(), "Burst must not fire twice after a skip");
        helper.succeed();
    }

    /** Skipping never rewinds a celebration that has already reached the wind-down. */
    public static void skipDoesNotRewind(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.settleStart() + 5f);
        float before = celebration.getTime();
        celebration.skipToSettle();

        helper.assertTrue(celebration.getTime() == before,
            "Skip must not rewind, was " + before + " now " + celebration.getTime());
        helper.succeed();
    }

    /** Gameplay stays suppressed for the whole sequence, so nothing lands behind the hero item. */
    public static void gameplaySuppressedUntilFinished(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        helper.assertTrue(celebration.suppressesGameplay(), "Must suppress gameplay at the start");

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.settleStart());
        helper.assertTrue(celebration.suppressesGameplay(), "Must still suppress gameplay while settling");

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.total());
        helper.assertTrue(!celebration.suppressesGameplay(), "Must release gameplay once finished");
        helper.succeed();
    }

    /** The shake decays to nothing rather than rattling the HUD for the whole sequence. */
    /**
     * Two shakes with a lull between them: the impact rattle decays away as the item rises, then a
     * second builds through the suspense and cuts dead the instant the fish is revealed. The lull
     * is what makes the build legible — a rumble that never stopped would read as background noise.
     */
    public static void shakeDecaysThenRebuildsIntoTheReveal(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        celebration.advance(0.25f);
        helper.assertTrue(shakeMagnitude(celebration) > 0f, "Must shake on impact");

        advanceTo(celebration, timings.suspenseStart());
        float lull = shakeMagnitude(celebration);
        helper.assertTrue(lull < 0.05f, "Impact shake must have died away before the build, got " + lull);

        // Sampled just short of the swap, where the build is at its peak.
        advanceTo(celebration, revealSwapTime(timings) - 0.5f);
        helper.assertTrue(shakeMagnitude(celebration) > lull,
            "Suspense must rebuild the shake, got " + shakeMagnitude(celebration));

        advanceTo(celebration, revealSwapTime(timings));
        helper.assertTrue(shakeMagnitude(celebration) == 0f,
            "Shake must cut dead as the fish is revealed, got " + shakeMagnitude(celebration));
        helper.succeed();
    }

    private static float shakeMagnitude(CatchCelebration celebration) {
        return Math.abs(celebration.getShakeX()) + Math.abs(celebration.getShakeY());
    }

    // -------------------------------------------------------------------------
    // Forced quality override (debug tooling, but it shares the real loot path)
    // -------------------------------------------------------------------------

    /**
     * The quality/size stamp used by both the ordinary loot roll and the {@code forcequality}
     * debug override must set both components together — a stack carrying one without the other
     * describes a fish that could never have been caught.
     */
    public static void applyQualityAndSizeSetsBothComponents(GameTestHelper helper) {
        Registry<FishProfile> profiles = helper.getLevel().registryAccess()
            .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        ItemStack stack = new ItemStack(Items.COD);
        FishtasticFishItem.applyQualityAndSize(
            stack, FishQuality.Quality.LEGENDARY, helper.getLevel().getRandom(), profiles);

        helper.assertTrue(FishQualityHelper.getQuality(stack) == FishQuality.Quality.LEGENDARY,
            "Quality must be stamped, got " + FishQualityHelper.getQuality(stack));
        helper.assertTrue(ItemSizeHelper.hasSize(stack), "A size must be rolled alongside the quality");
        helper.assertTrue(ItemSizeHelper.getSize(stack) > 0f,
            "Rolled size must be positive, got " + ItemSizeHelper.getSize(stack));
        helper.succeed();
    }

    /** Restamping an already-rolled stack replaces its quality rather than layering a second one. */
    public static void applyQualityAndSizeOverwritesExistingQuality(GameTestHelper helper) {
        Registry<FishProfile> profiles = helper.getLevel().registryAccess()
            .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        ItemStack stack = commonFish();
        FishtasticFishItem.applyQualityAndSize(
            stack, FishQuality.Quality.LEGENDARY, helper.getLevel().getRandom(), profiles);

        helper.assertTrue(FishQualityHelper.getQuality(stack) == FishQuality.Quality.LEGENDARY,
            "Forced quality must replace the rolled one, got " + FishQualityHelper.getQuality(stack));
        helper.succeed();
    }

    /** Rejects NONE outright — a tier of "no celebration" has no timeline to run. */
    public static void noneTierIsRejected(GameTestHelper helper) {
        boolean threw = false;
        try {
            new CatchCelebration(CatchCelebration.Tier.NONE, commonFish(), 0f, new Random(1234L));
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        helper.assertTrue(threw, "Constructing with Tier.NONE must throw");
        helper.succeed();
    }
}
