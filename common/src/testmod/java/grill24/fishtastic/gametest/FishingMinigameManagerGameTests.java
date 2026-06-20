package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.server.FishingMinigameManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Server-side game tests for FishingMinigameManager — the trust boundary between a client-played
 * minigame and server-awarded rewards (see the class javadoc: "Server validates and awards only
 * the items that were actually in the session").
 *
 * Two different setups are used here:
 * - startSession/cancelSession tests exercise the real RNG-driven path end to end (real
 *   FishingHook owned by a real mock player, real loot/fish-profile registry content) to prove
 *   the happy path doesn't throw and produces a usable session id.
 * - handleMinigameComplete tests use FishingMinigameManager.seedSessionForTest, a test-only seam
 *   added specifically because the real session contents are RNG-driven and not otherwise
 *   inspectable/controllable from outside the class — see the test-coverage roadmap's Tier 3.1
 *   writeup for why a black-box-only approach doesn't work here.
 *
 * Each test takes a {@code mockPlayer} supplier rather than calling
 * GameTestHelper.makeMockServerPlayerInLevel() directly — see TutorialManagerGameTests for why
 * (NeoForge's mock connection needs extra pre-configuration before it can receive the custom
 * packets startSession/handleMinigameComplete send, via StartFishingMinigamePacket/QuestSyncPacket).
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class FishingMinigameManagerGameTests {

    private FishingMinigameManagerGameTests() {}

    private static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** Spawning a FishingHook owned by a player casts the line — wires player.fishing for startSession. */
    private static FishingHook castLine(GameTestHelper helper, ServerPlayer player) {
        FishingHook hook = new FishingHook(player, helper.getLevel(), 0, 0);
        helper.getLevel().addFreshEntity(hook);
        return hook;
    }

    // -------------------------------------------------------------------------
    // startSession — real RNG-driven path, end to end
    // -------------------------------------------------------------------------

    /** The core spike from the roadmap: a real hook + rod must produce a valid, trackable session. */
    public static void startSessionEndToEndReturnsValidSessionId(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value()));
        FishingHook hook = castLine(helper, player);
        helper.assertTrue(player.fishing == hook,
            "Spawning a FishingHook owned by the player must set player.fishing (vanilla FishingHook.setOwner behavior) — startSession reads this field directly");

        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());
        int sessionId = manager.startSession(player, 1.0f, false);

        helper.assertTrue(sessionId != -1,
            "startSession with no pre-existing session and a live FishingHook must return a valid session id, got " + sessionId);
        helper.assertTrue(manager.isPlayerInActiveSession(player.getUUID()),
            "After startSession succeeds, the player must be tracked as having an active session");
        helper.succeed();
    }

    /** Duplicate-session guard: refuses a second session unless the caller explicitly opts to cancel the first. */
    public static void startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value()));
        castLine(helper, player);

        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());
        int firstSessionId = manager.startSession(player, 1.0f, false);
        helper.assertTrue(firstSessionId != -1, "Sanity check: the first startSession call must succeed");

        int secondSessionId = manager.startSession(player, 1.0f, false);
        helper.assertTrue(secondSessionId == -1,
            "startSession must refuse a second session for the same player while one is already active and cancelExistingSession is false");

        int thirdSessionId = manager.startSession(player, 1.0f, true);
        helper.assertTrue(thirdSessionId != -1 && thirdSessionId != firstSessionId,
            "startSession with cancelExistingSession=true must cancel the stale session and start a fresh one with a new id");
        helper.succeed();
    }

    public static void cancelSessionRemovesActiveSession(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        castLine(helper, player);
        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());
        manager.startSession(player, 1.0f, false);
        helper.assertTrue(manager.isPlayerInActiveSession(player.getUUID()), "Sanity check: startSession must mark the player active");

        manager.cancelSession(player);

        helper.assertTrue(!manager.isPlayerInActiveSession(player.getUUID()), "cancelSession must remove the player's active session");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // handleMinigameComplete — the actual trust-boundary validation
    // -------------------------------------------------------------------------

    /** Only the reported, in-range indices are awarded; everything else is silently ignored. */
    public static void handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());

        int sessionId = manager.seedSessionForTest(player, List.of(
            List.of(new ItemStack(Items.COD)),
            List.of(new ItemStack(Items.SALMON)),
            List.of(new ItemStack(Items.PUFFERFISH, 2))
        ));

        manager.handleMinigameComplete(player, sessionId, List.of(0, 2, 99, -1));

        helper.assertTrue(countItem(player, Items.COD) == 1, "Target index 0 was reported caught, its reward must be in the player's inventory");
        helper.assertTrue(countItem(player, Items.SALMON) == 0, "Target index 1 was never reported caught, its reward must not be awarded");
        helper.assertTrue(countItem(player, Items.PUFFERFISH) == 2, "Target index 2 was reported caught, its full reward stack must be in the player's inventory");
        helper.assertTrue(!manager.isPlayerInActiveSession(player.getUUID()), "A completed session must be removed regardless of how many indices were valid");
        helper.succeed();
    }

    /** An unknown or mismatched session id must be a safe no-op, even while a different real session is active. */
    public static void handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());

        manager.handleMinigameComplete(player, 12345, List.of(0));
        helper.assertTrue(countItem(player, Items.COD) == 0, "Completing a session id with no active session at all must not throw or award anything");

        int sessionId = manager.seedSessionForTest(player, List.of(List.of(new ItemStack(Items.COD))));

        manager.handleMinigameComplete(player, sessionId + 1, List.of(0));
        helper.assertTrue(countItem(player, Items.COD) == 0, "A stale/mismatched session id must not award the real active session's rewards");
        helper.assertTrue(manager.isPlayerInActiveSession(player.getUUID()), "A mismatched session id must not consume the real active session");
        helper.succeed();
    }

    /** A session is single-use: its first completion report consumes it even if every reported index was invalid. */
    public static void handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());

        int sessionId = manager.seedSessionForTest(player, List.of(List.of(new ItemStack(Items.COD))));

        manager.handleMinigameComplete(player, sessionId, List.of(99));
        helper.assertTrue(countItem(player, Items.COD) == 0, "Sanity check: an all-invalid-index report must not award anything");
        helper.assertTrue(!manager.isPlayerInActiveSession(player.getUUID()),
            "A session is consumed by its first completion report even if every index in it was invalid");

        manager.handleMinigameComplete(player, sessionId, List.of(0));
        helper.assertTrue(countItem(player, Items.COD) == 0,
            "Replaying a sessionId after it was already completed/removed must not award rewards a second time");
        helper.succeed();
    }

    /**
     * Documents current behavior: a sub-20-tick completion only logs a warning, it does not
     * withhold the reward. Flagged in the test-coverage roadmap as a real product question
     * worth a deliberate decision, not assumed correct — this test pins down what the code
     * actually does today so a future behavior change shows up as an intentional test update,
     * not a silent regression either way.
     */
    public static void handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());

        int sessionId = manager.seedSessionForTest(player, List.of(List.of(new ItemStack(Items.COD))));
        // Completed on the same tick the session was seeded: timeTaken == 0, well under the 20-tick floor.
        manager.handleMinigameComplete(player, sessionId, List.of(0));

        helper.assertTrue(countItem(player, Items.COD) == 1,
            "Current behavior: completing in under 20 ticks only logs a warning, it does not withhold the reward");
        helper.succeed();
    }

    /** Bait is the per-cast consumable cost — it must only be spent when a completion report actually awarded something. */
    public static void handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());

        ItemStack rod = new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
        CopperFishingRod.setBait(rod, new ItemStack(FishtasticItems.WORMS.value(), 5));
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);

        int failedSessionId = manager.seedSessionForTest(player, List.of(List.of(new ItemStack(Items.COD))));
        manager.handleMinigameComplete(player, failedSessionId, List.of(99));
        helper.assertTrue(CopperFishingRod.getBait(player.getMainHandItem()).getCount() == 5,
            "Bait must not be consumed when a completion report awards nothing");

        int successSessionId = manager.seedSessionForTest(player, List.of(List.of(new ItemStack(Items.COD))));
        manager.handleMinigameComplete(player, successSessionId, List.of(0));
        helper.assertTrue(CopperFishingRod.getBait(player.getMainHandItem()).getCount() == 4,
            "Bait must be consumed exactly once when a completion report awards at least one reward");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // generateTargets — three-way fish/treasure/trash roll, real RNG path
    // -------------------------------------------------------------------------

    private static ItemStack rodWithBaitEffect(BaitEffect effect) {
        ItemStack rod = new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
        ItemStack bait = new ItemStack(FishtasticItems.WORMS.value());
        bait.set(FishtasticDataComponents.BAIT_EFFECT.value(), effect);
        CopperFishingRod.setBait(rod, bait);
        return rod;
    }

    /**
     * With trashChance forced to 1.0, every target in the session must resolve to a trash item —
     * trash is rolled before treasure/fish, so it must win regardless of their chances. Proves
     * trash genuinely competes against the combined fish pool rather than being unreachable.
     */
    public static void trashChanceOneAlwaysAwardsTrashItems(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, rodWithBaitEffect(
            new BaitEffect(0f, 1.0f, 1.0f, 0, 1.0f, 1.0f, 0f, Optional.empty(), List.of())));
        castLine(helper, player);

        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());
        int sessionId = manager.startSession(player, 1.0f, false);
        helper.assertTrue(sessionId != -1, "Sanity check: startSession must succeed");

        manager.handleMinigameComplete(player, sessionId, List.of(0, 1, 2, 3));

        boolean awardedAnyTrash = false;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            helper.assertTrue(!stack.is(ItemTags.FISHES),
                "trashChance=1.0 must never award a fish item, got " + stack.getItem());
            if (stack.is(FishtasticItemTags.TRASH)) awardedAnyTrash = true;
        }
        helper.assertTrue(awardedAnyTrash, "trashChance=1.0 must award at least one trash-tagged item across all targets");
        helper.succeed();
    }

    /**
     * With trashChance at 0 and treasureChance at 1.0, the trash branch must never fire —
     * proves adding the trash roll didn't regress the pre-existing treasure-only path.
     */
    public static void treasureChanceOneWithZeroTrashNeverAwardsTrash(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, rodWithBaitEffect(
            new BaitEffect(0f, 1.0f, 0.0f, 0, 1.0f, 1.0f, 0f, Optional.empty(), List.of())));
        castLine(helper, player);

        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());
        int sessionId = manager.startSession(player, 1.0f, false);
        helper.assertTrue(sessionId != -1, "Sanity check: startSession must succeed");

        manager.handleMinigameComplete(player, sessionId, List.of(0, 1, 2, 3));

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            helper.assertTrue(!stack.is(FishtasticItemTags.TRASH),
                "trashChance=0.0 must never award a trash item, got " + stack.getItem());
        }
        helper.succeed();
    }
}
