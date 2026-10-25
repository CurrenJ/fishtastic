package grill24.fishtastic.gametest;

import com.mojang.serialization.JsonOps;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.server.FishSchoolManager;
import grill24.fishtastic.server.FishSchoolSavedData;
import grill24.fishtastic.server.FishingMinigameManager;
import grill24.fishtastic.server.FishingPoolResolver;
import grill24.fishtastic.util.IFishingHookExtension;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Focused end-to-end checks for shared school reservation, rewards and persistence. */
public final class FishSchoolGameTests {
    private FishSchoolGameTests() {}

    private record ReadySchool(FishSchoolSavedData.Member member, FishSchoolSavedData.School school,
                               FishingHook hook) {}

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static FishingHook castLine(GameTestHelper helper, ServerPlayer player) {
        FishingHook hook = new FishingHook(player, helper.getLevel(), 0, 0);
        helper.getLevel().addFreshEntity(hook);
        return hook;
    }

    private static ReadySchool seedReadyMember(GameTestHelper helper, ServerPlayer player, long rewardSeed) {
        FishingHook hook = castLine(helper, player);
        long now = helper.getLevel().getGameTime();
        BlockPos anchor = hook.blockPosition();
        FishSchoolSavedData data = FishSchoolSavedData.get(helper.getLevel());
        FishSchoolSavedData.FormationSnapshot formation = FishSchoolSavedData.snapshot(
                FishingPoolResolver.resolveEnvironment(helper.getLevel(), anchor, null));
        FishSchoolSavedData.Reservation reservation = new FishSchoolSavedData.Reservation(player.getUUID(),
                hook.getUUID(), now, now + 1000, 0, -1);
        FishSchoolSavedData.School school = data.addSchoolForTest(Fishtastic.id("bluegill"), anchor, now,
                now + 12_000, formation, List.of(new FishSchoolSavedData.MemberFixture(rewardSeed,
                        FishSchoolSavedData.Position.at(anchor), FishSchoolSavedData.MemberState.BITE_READY,
                        reservation)));
        FishSchoolSavedData.Member member = school.members().get(0);
        return new ReadySchool(member, school, hook);
    }

    public static void caughtSchoolTargetDepletesOnce(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value()));
        ReadySchool ready = seedReadyMember(helper, player, 0x5EED_1234L);
        FishSchoolManager schools = FishSchoolManager.get(helper.getLevel());
        FishingMinigameManager fishing = FishingMinigameManager.get(helper.getLevel());

        helper.assertTrue(schools.ownsBite(ready.hook()), "The reserved bite must be owned by this school hook");
        int sessionId = fishing.startSession(player, 1.0f, false);
        int schoolIndex = fishing.schoolTargetIndexForTest(player.getUUID());
        ItemStack reward = fishing.targetRewardForTest(player.getUUID(), schoolIndex);
        helper.assertTrue(sessionId >= 0 && schoolIndex >= 0,
                "A live bite-ready member must bind to the started minigame session");
        helper.assertTrue(reward != null && reward.is(FishtasticItems.BLUEGILL.value()),
                "The bound target slot must contain the reserved species");
        helper.assertTrue(fishing.targetCountForTest(player.getUUID()) >= 1
                        && fishing.targetCountForTest(player.getUUID()) <= 4,
                "A school catch must remain within the ordinary minigame target budget");
        helper.assertTrue(ready.member().state() == FishSchoolSavedData.MemberState.IN_SESSION,
                "Binding must move the shared member into its in-session state");
        helper.assertTrue(!schools.ownsBite(ready.hook()), "Starting the minigame must clear the hook's bite state");

        fishing.handleMinigameComplete(player, sessionId, List.of(schoolIndex, schoolIndex));
        int delivered = count(player, FishtasticItems.BLUEGILL.value());
        helper.assertTrue(delivered == 1, "A duplicate target index must deliver exactly one school fish");
        helper.assertTrue(ready.member().state() == FishSchoolSavedData.MemberState.CAUGHT
                        && ready.member().reservation() == null,
                "A successful target must deplete the member before reward delivery");

        fishing.handleMinigameComplete(player, sessionId, List.of(schoolIndex));
        helper.assertTrue(count(player, FishtasticItems.BLUEGILL.value()) == delivered,
                "Replaying a completed session must not award or progress the same school fish twice");
        helper.succeed();
    }

    public static void missedTargetRecoversAndRetryKeepsReward(GameTestHelper helper,
                                                               Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value()));
        ReadySchool ready = seedReadyMember(helper, player, 0x1234_5678_9ABCL);
        FishSchoolManager schools = FishSchoolManager.get(helper.getLevel());
        FishingMinigameManager fishing = FishingMinigameManager.get(helper.getLevel());

        int firstSession = fishing.startSession(player, 1.0f, false);
        int firstIndex = fishing.schoolTargetIndexForTest(player.getUUID());
        ItemStack firstReward = fishing.targetRewardForTest(player.getUUID(), firstIndex);
        helper.assertTrue(firstSession >= 0 && firstReward != null,
                "The first school attempt must create a bound reward");
        fishing.handleMinigameComplete(player, firstSession, List.of());
        helper.assertTrue(count(player, FishtasticItems.BLUEGILL.value()) == 0,
                "Escaping the school target must not grant the school reward");
        helper.assertTrue(ready.member().state() == FishSchoolSavedData.MemberState.RECOVERING
                        && ready.member().reservation() == null,
                "A missed target must release its reservation into recovery");

        long now = helper.getLevel().getGameTime();
        ready.hook().discard();
        FishingHook retryHook = castLine(helper, player);
        FishSchoolSavedData.get(helper.getLevel()).setMemberForTest(ready.member(),
                FishSchoolSavedData.MemberState.BITE_READY,
                new FishSchoolSavedData.Reservation(player.getUUID(), retryHook.getUUID(), now, now + 1000, 0, -1));
        int retrySession = fishing.startSession(player, 1.0f, false);
        int retryIndex = fishing.schoolTargetIndexForTest(player.getUUID());
        ItemStack retryReward = fishing.targetRewardForTest(player.getUUID(), retryIndex);
        helper.assertTrue(retrySession >= 0 && retryReward != null
                        && retryReward.is(firstReward.getItem())
                        && FishQualityHelper.getQuality(retryReward) == FishQualityHelper.getQuality(firstReward)
                        && Float.compare(ItemSizeHelper.getSize(retryReward), ItemSizeHelper.getSize(firstReward)) == 0,
                "Retrying the same member with unchanged tackle must reproduce its species, quality and size");

        schools.releaseBinding(new FishSchoolManager.SessionBinding(ready.school().id(), ready.member().id(),
                player.getUUID(), retrySession, retryIndex, ready.school().species(), ready.member().rewardSeed()));
        helper.assertTrue(ready.member().state() == FishSchoolSavedData.MemberState.RECOVERING,
                "Cancelling a retried session must also release the member");
        helper.succeed();
    }

    public static void liveSchoolApproachesAndTriggersOwnedBite(GameTestHelper helper,
                                                                Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value()));
        BlockPos testOrigin = helper.absolutePos(new BlockPos(1, 1, 1));
        int waterY = helper.getLevel().getSeaLevel();
        BlockPos anchor = new BlockPos(testOrigin.getX() + 2, waterY, testOrigin.getZ() + 2);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                helper.getLevel().setBlock(anchor.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 3);
            }
        }
        player.setPos(anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5);
        FishingHook hook = new FishingHook(player, helper.getLevel(), 0, 0);
        hook.setPos(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        hook.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(hook);
        // GameTest's mock player does not retain the current bobber link the way the normal
        // fishing-rod use path does, especially while many fixtures tick concurrently.
        player.fishing = hook;

        long now = helper.getLevel().getGameTime();
        FishSchoolSavedData data = FishSchoolSavedData.get(helper.getLevel());
        FishSchoolSavedData.FormationSnapshot formation = FishSchoolSavedData.snapshot(
                FishingPoolResolver.resolveEnvironment(helper.getLevel(), anchor, null));
        FishSchoolSavedData.School school = data.addSchoolForTest(Fishtastic.id("bluegill"), anchor, now,
                now + 12_000, formation, List.of(
                        new FishSchoolSavedData.MemberFixture(11L,
                                FishSchoolSavedData.Position.at(anchor.offset(2, 0, 0)),
                                FishSchoolSavedData.MemberState.AVAILABLE, null),
                        new FishSchoolSavedData.MemberFixture(19L,
                                FishSchoolSavedData.Position.at(anchor.offset(-2, 0, 0)),
                                FishSchoolSavedData.MemberState.AVAILABLE, null)));
        FishSchoolManager schools = FishSchoolManager.get(helper.getLevel());
        schools.tick();
        awaitOwnedBite(helper, player, hook, schools, school, 0);
    }

    /** Waits for whichever member the school's engine brings to the bobber; which one is the swim's business. */
    private static void awaitOwnedBite(GameTestHelper helper, ServerPlayer player, FishingHook hook,
                                       FishSchoolManager schools, FishSchoolSavedData.School school,
                                       int ticksWaited) {
        if (!hook.isRemoved()) player.fishing = hook;
        schools.tick();
        FishSchoolSavedData.Member member = school.members().stream()
                .filter(m -> m.state() == FishSchoolSavedData.MemberState.BITE_READY)
                .findFirst().orElse(null);
        if (member != null) {
            helper.assertTrue(schools.ownsBite(hook) && ((IFishingHookExtension) hook).fishtastic$isBiting(),
                    "A visible approach must create exactly one school-owned vanilla bobber bite");
            FishingMinigameManager fishing = FishingMinigameManager.get(helper.getLevel());
            int sessionId = fishing.startSession(player, 1.0f, false);
            helper.assertTrue(sessionId >= 0 && fishing.schoolTargetIndexForTest(player.getUUID()) >= 0,
                    "The school-owned bite must bind to the minigame when reeled in");
            helper.assertTrue(member.state() == FishSchoolSavedData.MemberState.IN_SESSION,
                    "Reeling in must reserve the approached member for its minigame");
            helper.assertTrue(!schools.memberInWaterForTest(school.id(), member.id()),
                    "A hooked member must leave the school's water for its fight");
            fishing.handleMinigameComplete(player, sessionId, List.of());
            helper.assertTrue(member.state() == FishSchoolSavedData.MemberState.RECOVERING
                            && schools.memberInWaterForTest(school.id(), member.id()),
                    "A member that got away must be back in the school's water, recovering");
            helper.succeed();
            return;
        }
        FishingMinigameManager fishing = FishingMinigameManager.get(helper.getLevel());

        helper.assertTrue(ticksWaited < 300,
                "A member in valid connected water must approach and start a bite; final states were "
                        + school.members().stream().map(m -> m.state().toString()).toList()
                        + ", players=" + helper.getLevel().players().size() + ", hook=" + hook.blockPosition()
                        + ", schoolAnchor=" + school.anchor()
                        + ", anchorLoaded=" + helper.getLevel().hasChunkAt(school.anchor())
                        + ", hookFluid=" + helper.getLevel().getFluidState(hook.blockPosition())
                        + ", hookRemoved=" + hook.isRemoved()
                        + ", ownerHasHook=" + (player.fishing == hook)
                        + ", activeSession=" + fishing.isPlayerInActiveSession(player.getUUID())
                        + ", ownedBite=" + schools.ownsBite(hook)
                        + ", " + schools.debugState(school.id()));
        helper.runAfterDelay(1, () -> awaitOwnedBite(helper, player, hook, schools, school, ticksWaited + 1));
    }

    /**
     * The school's engine keeps every member in its visit's water, round an obstacle in the middle
     * of it, and steps exactly once per game tick however often the manager is ticked: every client
     * copy steps once per tick, so a server that ran ahead would leave them all behind.
     */
    public static void schoolStaysInItsWaterAndStepsOncePerTick(GameTestHelper helper) {
        BlockPos testOrigin = helper.absolutePos(new BlockPos(1, 1, 1));
        int waterY = helper.getLevel().getSeaLevel();
        BlockPos center = new BlockPos(testOrigin.getX() + 2, waterY, testOrigin.getZ() + 2);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -1; y <= 0; y++) {
                    boolean pillar = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                    helper.getLevel().setBlock(center.offset(x, y, z),
                            pillar ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        BlockPos anchor = center.offset(3, 0, 0);
        long now = helper.getLevel().getGameTime();
        FishSchoolSavedData data = FishSchoolSavedData.get(helper.getLevel());
        FishSchoolSavedData.FormationSnapshot formation = FishSchoolSavedData.snapshot(
                FishingPoolResolver.resolveEnvironment(helper.getLevel(), anchor, null));
        List<FishSchoolSavedData.MemberFixture> fixtures = new java.util.ArrayList<>();
        int[][] spots = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int i = 0; i < spots.length; i++) {
            fixtures.add(new FishSchoolSavedData.MemberFixture(31L + i,
                    FishSchoolSavedData.Position.at(center.offset(spots[i][0], 0, spots[i][1])),
                    FishSchoolSavedData.MemberState.AVAILABLE, null));
        }
        FishSchoolSavedData.School school = data.addSchoolForTest(Fishtastic.id("bluegill"), anchor,
                now, now + 12_000, formation, fixtures);
        FishSchoolManager schools = FishSchoolManager.get(helper.getLevel());
        schools.tick();
        awaitContainedSteps(helper, schools, school, schools.simStepsForTest(school.id()), 0);
    }

    private static void awaitContainedSteps(GameTestHelper helper, FishSchoolManager schools,
                                            FishSchoolSavedData.School school, long firstStep, int ticksWaited) {
        // Ticked again on purpose, as the bite test does: the extra calls must not step the engine.
        schools.tick();
        schools.tick();
        long steps = schools.simStepsForTest(school.id());
        helper.assertTrue(firstStep >= 0 && steps == firstStep + ticksWaited,
                "The school's engine must step once per game tick: " + steps + " steps after " + ticksWaited
                        + " ticks from " + firstStep);
        for (FishSchoolSavedData.Member member : school.members()) {
            BlockPos at = BlockPos.containing(member.position().x(), member.position().y(), member.position().z());
            helper.assertTrue(member.state() == FishSchoolSavedData.MemberState.AVAILABLE
                            && helper.getLevel().getBlockState(at).is(Blocks.WATER),
                    "Member left its water at tick " + ticksWaited + ": " + at + ", "
                            + schools.debugState(school.id()));
        }
        if (ticksWaited >= 300) {
            helper.succeed();
            return;
        }
        helper.runAfterDelay(1, () -> awaitContainedSteps(helper, schools, school, firstStep, ticksWaited + 1));
    }

    public static void transientReservationsRecoverAcrossSaveReload(GameTestHelper helper) {
        FishSchoolSavedData data = new FishSchoolSavedData();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        var environment = FishingPoolResolver.resolveEnvironment(helper.getLevel(), anchor, null);
        FishSchoolSavedData.Reservation reservation = new FishSchoolSavedData.Reservation(UUID.randomUUID(),
                UUID.randomUUID(), 40L, 120L, 9, 0);
        data.addSchoolForTest(Fishtastic.id("bluegill"), anchor, 10L, 500L,
                FishSchoolSavedData.snapshot(environment), List.of(
                        new FishSchoolSavedData.MemberFixture(17L, FishSchoolSavedData.Position.at(anchor),
                                FishSchoolSavedData.MemberState.IN_SESSION, reservation),
                        new FishSchoolSavedData.MemberFixture(23L,
                                FishSchoolSavedData.Position.at(anchor.offset(1, 0, 0)),
                                FishSchoolSavedData.MemberState.CAUGHT, null)));
        data.setRegionForTest(new FishSchoolSavedData.RegionSchedule(2, -3, 24000L, 20L));

        var encoded = FishSchoolSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(false, error -> { throw new AssertionError(error); });
        FishSchoolSavedData restored = FishSchoolSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(false, error -> { throw new AssertionError(error); });
        FishSchoolSavedData.School restoredSchool = restored.schoolsForTest().get(0);
        FishSchoolSavedData.Member restoredInFlight = restoredSchool.members().get(0);
        FishSchoolSavedData.Member restoredCaught = restoredSchool.members().get(1);
        helper.assertTrue(restoredInFlight.state() == FishSchoolSavedData.MemberState.RECOVERING
                        && restoredInFlight.reservation() == null,
                "A reload must clear non-restorable hook/session ownership and recover the member");
        helper.assertTrue(restoredCaught.state() == FishSchoolSavedData.MemberState.CAUGHT,
                "A completed catch must remain depleted after reload");
        helper.assertTrue(restored.regionsForTest().size() == 1
                        && restored.regionsForTest().get(0).nextAttemptAt() == 24000L,
                "The regional encounter deadline must survive reload");
        helper.succeed();
    }
}
