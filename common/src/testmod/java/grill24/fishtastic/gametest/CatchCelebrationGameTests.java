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
        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.hangStart() + CatchCelebration.SKIP_GRACE_TICKS);
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
        helper.assertTrue(!celebration.isSkippable(), "Must not be skippable in the grace period right after the reveal");
        celebration.skipToSettle();
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.HANG,
            "Skip during the grace period must be ignored, got " + celebration.getPhase());

        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.hangStart() + CatchCelebration.SKIP_GRACE_TICKS);
        helper.assertTrue(celebration.isSkippable(), "Must be skippable once the grace period has passed");
        helper.succeed();
    }

    /** The sparkle burst fires on its own at the reveal even if the player skips right after it. */
    public static void skipAfterRevealKeepsBurstConsumed(GameTestHelper helper) {
        CatchCelebration celebration = heroCelebration();
        advanceTo(celebration, CatchCelebration.HERO_TIMINGS.hangStart() + CatchCelebration.SKIP_GRACE_TICKS);

        helper.assertTrue(celebration.consumeSparkleBurst(), "Burst must be pending at the hang");
        celebration.skipToSettle();
        helper.assertTrue(!celebration.consumeSparkleBurst(), "Burst must not fire twice after a skip");
        helper.succeed();
    }

    /**
     * Both tiers hold on the revealed fish for several real seconds (20 ticks each) before it is
     * released: 8 s for the hero moment, 5 s for a discovery.
     */
    public static void holdsLastSeveralSeconds(GameTestHelper helper) {
        helper.assertTrue(CatchCelebration.HERO_TIMINGS.hang() >= 5f * 20f && CatchCelebration.HERO_TIMINGS.hang() <= 10f * 20f,
            "Hero hold must be 5-10 s, got " + CatchCelebration.HERO_TIMINGS.hang() / 20f + " s");
        helper.assertTrue(CatchCelebration.DISCOVERY_TIMINGS.hang() >= 5f * 20f && CatchCelebration.DISCOVERY_TIMINGS.hang() <= 10f * 20f,
            "Discovery hold must be 5-10 s, got " + CatchCelebration.DISCOVERY_TIMINGS.hang() / 20f + " s");

        // Left alone, the celebration stays on the revealed fish right up to the end of the hold.
        CatchCelebration celebration = heroCelebration();
        float nearEnd = CatchCelebration.HERO_TIMINGS.settleStart() - 1f;
        advanceTo(celebration, nearEnd);
        helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.HANG, "Must still be holding, got " + celebration.getPhase());
        helper.assertTrue(!celebration.isSilhouetted(), "The fish must be revealed through the hold");
        helper.assertTrue(celebration.getRayAlpha() > 0.5f, "Rays must still be up late in the hold, got " + celebration.getRayAlpha());
        helper.assertTrue(celebration.getBannerAlpha() > 0.95f, "Banner must still be up late in the hold, got " + celebration.getBannerAlpha());
        helper.succeed();
    }

    /** Once past the grace period, a skip works at any point in either tier's hold. */
    public static void skipWorksThroughoutTheHold(GameTestHelper helper) {
        for (CatchCelebration.Tier tier : new CatchCelebration.Tier[]{CatchCelebration.Tier.HERO, CatchCelebration.Tier.DISCOVERY}) {
            CatchCelebration.Timings timings = tier == CatchCelebration.Tier.HERO
                ? CatchCelebration.HERO_TIMINGS : CatchCelebration.DISCOVERY_TIMINGS;
            for (float fraction : new float[]{0.2f, 0.5f, 0.95f}) {
                CatchCelebration celebration = new CatchCelebration(tier, commonFish(), 0f, new Random(1234L));
                advanceTo(celebration, timings.hangStart() + timings.hang() * fraction);
                helper.assertTrue(celebration.isSkippable(), tier + " must be skippable " + fraction + " of the way through the hold");
                celebration.skipToSettle();
                helper.assertTrue(celebration.getPhase() == CatchCelebration.Phase.SETTLE,
                    tier + " skip must land on SETTLE, got " + celebration.getPhase());
            }
        }
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

    // -------------------------------------------------------------------------
    // Hero polish: dim-and-release, punch, rays, banner
    // -------------------------------------------------------------------------

    /** Discovery wears the same layers as the hero moment, but quieter and in cooler colours. */
    public static void discoveryPolishIsSofterThanHero(GameTestHelper helper) {
        CatchCelebration.Polish hero = CatchCelebration.HERO_POLISH;
        CatchCelebration.Polish discovery = CatchCelebration.DISCOVERY_POLISH;
        helper.assertTrue(discovery.dimMax() < hero.dimMax(), "Discovery must dim less than the hero moment");
        helper.assertTrue(discovery.punchAmplitude() < hero.punchAmplitude(), "Discovery must punch less than the hero moment");
        helper.assertTrue(discovery.rayAlpha() < hero.rayAlpha(), "Discovery rays must be fainter");
        helper.assertTrue(discovery.confettiCount() < hero.confettiCount(), "Discovery must throw less confetti");
        helper.assertTrue(discovery.glowRgb() != hero.glowRgb(), "Discovery must wear its own colours");
        helper.assertTrue(!discovery.tagText().equals(hero.tagText()), "Discovery must have its own tag line");

        CatchCelebration.Timings timings = CatchCelebration.DISCOVERY_TIMINGS;
        CatchCelebration celebration = new CatchCelebration(
            CatchCelebration.Tier.DISCOVERY, commonFish(), 0f, new Random(1234L));
        helper.assertTrue(celebration.getPolish() == discovery, "A discovery celebration must use the discovery polish");

        advanceTo(celebration, revealSwapTime(timings) + 0.3f);
        helper.assertTrue(celebration.getHeroPunchScale() > 1.02f,
            "Discovery must still overshoot on the reveal, got " + celebration.getHeroPunchScale());

        advanceTo(celebration, revealSwapTime(timings) + 4f);
        helper.assertTrue(celebration.getRayAlpha() > 0.2f, "Discovery rays must be up, got " + celebration.getRayAlpha());

        advanceTo(celebration, revealSwapTime(timings) + 8f);
        helper.assertTrue(celebration.getBannerScale() > 0.5f, "Discovery banner must have popped in, got " + celebration.getBannerScale());
        helper.succeed();
    }

    /**
     * The reduced-effects setting removes flash, shake, dim, spin and bounce — everything that
     * moves the whole screen or flickers — while the reveal itself, a still glow and the banner stay.
     */
    public static void reducedEffectsStripFlashShakeDimAndMotion(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration reduced = new CatchCelebration(
            CatchCelebration.Tier.HERO, legendaryFish(), 0f, new Random(1234L), true);
        CatchCelebration full = heroCelebration();
        helper.assertTrue(reduced.isReducedEffects() && !full.isReducedEffects(), "The flag must be carried on the celebration");

        // Mid-suspense: the shake and dim are building for a normal celebration.
        float strain = timings.suspenseStart() + timings.suspense() * 0.8f;
        advanceTo(reduced, strain);
        advanceTo(full, strain);
        helper.assertTrue(full.getDimAlpha() > 0.1f && full.getShakeX() != 0f, "Sanity: a full celebration dims and shakes while straining");
        helper.assertTrue(reduced.getDimAlpha() == 0f, "Reduced must not dim, got " + reduced.getDimAlpha());
        helper.assertTrue(reduced.getShakeX() == 0f && reduced.getShakeY() == 0f, "Reduced must not shake");

        // Just after the reveal: flash, punch and spinning rays for a full celebration.
        float afterReveal = revealSwapTime(timings) + 0.3f;
        advanceTo(reduced, afterReveal);
        advanceTo(full, afterReveal);
        helper.assertTrue(full.getFlashAlpha() > 0f && full.getHeroPunchScale() != 1f, "Sanity: a full celebration flashes and punches on the reveal");
        helper.assertTrue(reduced.getFlashAlpha() == 0f, "Reduced must not flash, got " + reduced.getFlashAlpha());
        helper.assertTrue(reduced.getHeroPunchScale() == 1f, "Reduced must not punch, got " + reduced.getHeroPunchScale());

        advanceTo(reduced, revealSwapTime(timings) + 5f);
        advanceTo(full, revealSwapTime(timings) + 5f);
        helper.assertTrue(reduced.getRayAlpha() == 0f && reduced.getRaySpinDegrees() == 0f, "Reduced must not show or spin rays");
        helper.assertTrue(reduced.getGlowAlpha() > 0.1f && reduced.getGlowAlpha() < full.getGlowAlpha(),
            "Reduced keeps a still, dimmer glow: reduced " + reduced.getGlowAlpha() + " vs full " + full.getGlowAlpha());
        helper.assertTrue(reduced.getBannerScale() == 1f, "Reduced banner appears at full size with no overshoot, got " + reduced.getBannerScale());
        helper.assertTrue(reduced.getBannerAlpha() > 0.9f, "Reduced banner must still show, got " + reduced.getBannerAlpha());
        helper.assertTrue(reduced.getWaveAmplitude() == 0f && full.getWaveAmplitude() > 0f, "Reduced text must not hop");
        helper.succeed();
    }

    /** The room darkens through the strain, then lets go almost immediately on the reveal. */
    public static void heroDimBuildsThenReleasesAtReveal(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        helper.assertTrue(celebration.getDimAlpha() == 0f, "Must start undimmed, got " + celebration.getDimAlpha());

        advanceTo(celebration, revealSwapTime(timings) - 0.25f);
        float atPeak = celebration.getDimAlpha();
        helper.assertTrue(atPeak > 0.3f, "Must be nearly fully dimmed just before the reveal, got " + atPeak);

        advanceTo(celebration, revealSwapTime(timings) + 8f);
        helper.assertTrue(celebration.getDimAlpha() < 0.02f,
            "Must have released shortly after the reveal, got " + celebration.getDimAlpha());
        helper.succeed();
    }

    /** The fish overshoots as it resolves, then rings out to exactly its normal size. */
    public static void heroPunchOvershootsThenSettles(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        advanceTo(celebration, revealSwapTime(timings) - 0.5f);
        helper.assertTrue(celebration.getHeroPunchScale() == 1f, "Must not punch before the reveal");

        advanceTo(celebration, revealSwapTime(timings) + 0.3f);
        helper.assertTrue(celebration.getHeroPunchScale() > 1.02f,
            "Must overshoot just after the reveal, got " + celebration.getHeroPunchScale());

        advanceTo(celebration, revealSwapTime(timings) + 8f);
        helper.assertTrue(Math.abs(celebration.getHeroPunchScale() - 1f) < 0.005f,
            "Must have settled back to normal size, got " + celebration.getHeroPunchScale());
        helper.succeed();
    }

    /** Rays swell in on the reveal, spin, and are gone by the end of the wind-down. */
    public static void heroRaysAppearOnRevealAndFadeOut(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        advanceTo(celebration, revealSwapTime(timings) - 0.5f);
        helper.assertTrue(celebration.getRayAlpha() == 0f, "No rays before the reveal");

        advanceTo(celebration, revealSwapTime(timings) + 2f);
        helper.assertTrue(celebration.getRayAlpha() > 0.3f, "Rays must be up shortly after the reveal, got " + celebration.getRayAlpha());
        helper.assertTrue(celebration.getRaySpinDegrees() > 0f, "Rays must be turning");

        advanceTo(celebration, timings.total());
        helper.assertTrue(celebration.getRayAlpha() < 0.01f, "Rays must be gone by the end, got " + celebration.getRayAlpha());
        helper.succeed();
    }

    /** The banner pops in with an overshoot after the reveal, stays through the hang, and leaves. */
    public static void heroBannerPopsInWithOvershootThenLeaves(GameTestHelper helper) {
        CatchCelebration.Timings timings = CatchCelebration.HERO_TIMINGS;
        CatchCelebration celebration = heroCelebration();

        advanceTo(celebration, revealSwapTime(timings) - 0.5f);
        helper.assertTrue(celebration.getBannerScale() == 0f, "No banner before the reveal");

        float peak = 0f;
        for (float t = revealSwapTime(timings); t < revealSwapTime(timings) + 5f; t += 0.1f) {
            advanceTo(celebration, t);
            peak = Math.max(peak, celebration.getBannerScale());
        }
        helper.assertTrue(peak > 1.02f, "Banner must overshoot its final size while popping in, peak " + peak);

        advanceTo(celebration, timings.hangStart() + timings.hang() * 0.5f);
        helper.assertTrue(celebration.getBannerAlpha() > 0.95f,
            "Banner must be fully visible mid-hang, got " + celebration.getBannerAlpha());

        advanceTo(celebration, timings.total());
        helper.assertTrue(celebration.getBannerAlpha() < 0.01f,
            "Banner must be gone by the end, got " + celebration.getBannerAlpha());
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
