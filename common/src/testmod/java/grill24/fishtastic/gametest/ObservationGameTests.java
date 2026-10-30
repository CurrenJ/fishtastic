package grill24.fishtastic.gametest;

import com.mojang.serialization.JavaOps;
import grill24.FishtasticRegistries;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.encyclopedia.FishPageModel;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.AmbientRoster;
import grill24.fishtastic.ecology.ObservationRules;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.ObserveFishPacket;
import grill24.fishtastic.server.EcologySeedSync;
import grill24.fishtastic.server.FishCatchBackups;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.FishObservation;
import grill24.fishtastic.server.FishObservation.Result;
import grill24.fishtastic.server.FishSchoolManager;
import grill24.fishtastic.server.FishSchoolSavedData;
import grill24.fishtastic.server.FishingPoolResolver;
import grill24.fishtastic.util.Ids;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Spyglass observation (docs/world-fish-exploration-implementation-plan.md, Step 5) against a real
 * level and a mock player: a valid claim records once and a bad one is refused, the packets are
 * rate limited, an observation grants a name, portrait and habitat and nothing else, and the
 * observed set survives save, reload, backup and restore.
 *
 * <p>The player's eye and look are placed by hand, so the cone and range checks run for real. Every
 * test is synchronous (no ticks pass), so the fixture visits are never moved or removed by the
 * manager mid-test.
 */
public final class ObservationGameTests {

    private ObservationGameTests() {}

    private static final Identifier BLUEGILL = Ids.of("fishtastic", "bluegill");

    /** A far-apart clock for each packet, so the rate limit never interferes with a test that is not about it. */
    private static long clock = 1_000_000L;

    private static long nextTick() {
        clock += 1000L;
        return clock;
    }

    private static FishCatchSavedData live(GameTestHelper helper) {
        return FishCatchSavedData.getOrCreate(helper.getLevel().getServer());
    }

    /** A 13 x 13 pond three blocks deep at sea level in the test area; its centre cell is water. */
    private static BlockPos pond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos center = new BlockPos(origin.getX() + 2, level.getSeaLevel(), origin.getZ() + 2);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                level.setBlock(center.offset(x, -3, z), Blocks.SAND.defaultBlockState(), 3);
                for (int y = -2; y <= 0; y++) {
                    level.setBlock(center.offset(x, y, z), Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        return center;
    }

    /** Puts the player's eye at {@code eye} looking at {@code target} (or away from it), through a spyglass. */
    private static void aim(ServerPlayer player, Vec3 eye, Vec3 target, boolean away) {
        if (!player.isScoping()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
            player.startUsingItem(InteractionHand.MAIN_HAND);
        }
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + (away ? 180f : 0f);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))) * (away ? -1f : 1f);
        player.snapTo(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** One live visit of {@code species} with a single member in the pond. */
    private static FishSchoolSavedData.School visitIn(GameTestHelper helper, BlockPos pond, Identifier species) {
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        FishSchoolSavedData.FormationSnapshot formation = FishSchoolSavedData.snapshot(
                FishingPoolResolver.resolveEnvironment(level, pond, null));
        return FishSchoolSavedData.get(level).addSchoolForTest(species, pond, now, now + 12_000, formation,
                List.of(new FishSchoolSavedData.MemberFixture(7L, FishSchoolSavedData.Position.at(pond),
                        FishSchoolSavedData.MemberState.AVAILABLE, null)));
    }

    private static void forget(GameTestHelper helper, FishSchoolSavedData.School school) {
        FishSchoolManager.get(helper.getLevel()).forgetVisitForTest(school);
    }

    private static ObserveFishPacket visitPacket(FishSchoolSavedData.School school) {
        return ObserveFishPacket.visit(school.species(), school.id(), school.members().get(0).id());
    }

    // ------------------------------------------------------------------ visit members

    public static void aValidVisitObservationRecordsOnce(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishObservation.resetForTest();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer.get();
        BlockPos pond = pond(helper);
        FishSchoolSavedData.School school = visitIn(helper, pond, BLUEGILL);
        try {
            FishCatchSavedData data = live(helper);
            UUID key = data.resolvePlayerKey(player);
            helper.assertTrue(!data.hasObserved(key, BLUEGILL), "setup: not observed yet");

            Vec3 fish = center(pond);
            aim(player, fish.add(12, 0, 0), fish, false);
            Result first = FishObservation.handle(player, visitPacket(school), nextTick());
            helper.assertTrue(first == Result.RECORDED, "a member in range and in the cone is recorded, got " + first);
            helper.assertTrue(data.hasObserved(key, BLUEGILL), "the species is now observed");
            helper.assertTrue(data.getObserved(key).equals(java.util.Set.of(BLUEGILL)), "only that species is observed");

            Result second = FishObservation.handle(player, visitPacket(school), nextTick());
            helper.assertTrue(second == Result.ALREADY_KNOWN, "a second claim for a known species adds nothing, got " + second);
            helper.assertTrue(!data.recordObservation(key, BLUEGILL), "recording again reports it was not new");
            helper.assertTrue(data.getObserved(key).size() == 1, "still exactly one observed species");
            data.clearObserved(key);
        } finally {
            forget(helper, school);
        }
        helper.succeed();
    }

    public static void badVisitClaimsAreRejected(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishObservation.resetForTest();
        ServerPlayer player = mockPlayer.get();
        BlockPos pond = pond(helper);
        FishSchoolSavedData.School school = visitIn(helper, pond, BLUEGILL);
        try {
            FishCatchSavedData data = live(helper);
            UUID key = data.resolvePlayerKey(player);
            Vec3 fish = center(pond);

            aim(player, fish.add(12, 0, 0), fish, false);
            // A school that does not exist.
            Result unknownSchool = FishObservation.handle(player,
                    ObserveFishPacket.visit(BLUEGILL, UUID.randomUUID(), school.members().get(0).id()), nextTick());
            helper.assertTrue(unknownSchool == Result.NO_SUCH_FISH, "unknown school, got " + unknownSchool);
            // A member that is not in it.
            Result unknownMember = FishObservation.handle(player,
                    ObserveFishPacket.visit(BLUEGILL, school.id(), UUID.randomUUID()), nextTick());
            helper.assertTrue(unknownMember == Result.NO_SUCH_FISH, "unknown member, got " + unknownMember);
            // The right fish named as another species.
            Identifier other = Ids.of("fishtastic", "rainbow_trout");
            helper.assertTrue(FishObservation.isObservableSpecies(helper.getLevel(), other), "setup: the other species is observable");
            Result wrongSpecies = FishObservation.handle(player,
                    ObserveFishPacket.visit(other, school.id(), school.members().get(0).id()), nextTick());
            helper.assertTrue(wrongSpecies == Result.WRONG_SPECIES, "wrong species, got " + wrongSpecies);
            // Not a species at all.
            Result notSpecies = FishObservation.handle(player,
                    ObserveFishPacket.visit(Ids.parse("minecraft:stone"), school.id(), school.members().get(0).id()), nextTick());
            helper.assertTrue(notSpecies == Result.UNKNOWN_SPECIES, "not a fish species, got " + notSpecies);

            // Far away: out of range, whichever way the player looks.
            aim(player, fish.add(ObservationRules.MAX_RANGE + 40, 0, 0), fish, false);
            Result far = FishObservation.handle(player, visitPacket(school), nextTick());
            helper.assertTrue(far == Result.OUT_OF_RANGE, "a far-away fish is refused, got " + far);

            // In range but looking the other way.
            aim(player, fish.add(12, 0, 0), fish, true);
            Result away = FishObservation.handle(player, visitPacket(school), nextTick());
            helper.assertTrue(away == Result.NOT_LOOKING, "looking away is refused, got " + away);

            // In range and in the cone, but not through a spyglass.
            aim(player, fish.add(12, 0, 0), fish, false);
            player.stopUsingItem();
            Result bareEye = FishObservation.handle(player, visitPacket(school), nextTick());
            helper.assertTrue(bareEye == Result.NOT_SCOPING, "no spyglass is refused, got " + bareEye);

            // A member already caught is not in the water.
            FishSchoolSavedData.get(helper.getLevel()).setMemberForTest(school.members().get(0),
                    FishSchoolSavedData.MemberState.CAUGHT, null);
            aim(player, fish.add(12, 0, 0), fish, false);
            Result caught = FishObservation.handle(player, visitPacket(school), nextTick());
            helper.assertTrue(caught == Result.NO_SUCH_FISH, "a caught member is gone, got " + caught);

            helper.assertTrue(data.getObserved(key).isEmpty(), "none of the refused claims recorded anything");
        } finally {
            forget(helper, school);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ ambient fish

    /** An ambient fish that exists now in the pond's chunk, with a species its home cell could draw. */
    private record Ambient(int chunkX, int chunkZ, int slot, long index, long id, BlockPos home, Identifier species,
                           Identifier impossible) {
        ObserveFishPacket packet() {
            return ObserveFishPacket.ambient(species, id, chunkX, chunkZ, slot, index, home);
        }
    }

    private static Ambient findAmbient(GameTestHelper helper, BlockPos home, long now) {
        ServerLevel level = helper.getLevel();
        long seed = EcologySeedSync.seedFor(level);
        int cx = home.getX() >> 4, cz = home.getZ() >> 4;
        AmbientRoster.Lifespan present = null;
        for (int slot = 0; slot < AmbientRoster.DEFAULT.maxSlotsPerChunk() && present == null; slot++) {
            AmbientRoster.Lifespan candidate = AmbientRoster.lifespanAt(seed, cx, cz, slot, now, AmbientRoster.DEFAULT);
            // Keep the same fish through every packet in the synchronous refusal tests below.
            if (candidate != null && candidate.departureTick() > now + 1000L) present = candidate;
        }
        helper.assertTrue(present != null, "some slot of the chunk must hold a fish now");
        Registry<FishProfile> profiles = level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        Identifier possible = null, impossible = null;
        for (Identifier id : profiles.keySet()) {
            if (!FishObservation.isObservableSpecies(level, id)) continue;
            boolean holds = FishObservation.poolHoldsAmbient(level, home, present.arrivalTick(), id);
            if (holds && possible == null) possible = id;
            if (!holds && impossible == null) impossible = id;
        }
        helper.assertTrue(possible != null, "the pond's pool must offer an ambient species");
        helper.assertTrue(impossible != null, "some species must be impossible at the pond");
        return new Ambient(cx, cz, present.slot(), present.index(), present.fishId(), home, possible, impossible);
    }

    public static void aValidAmbientObservationRecords(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishObservation.resetForTest();
        ServerPlayer player = mockPlayer.get();
        BlockPos home = pond(helper);
        long now = nextTick();
        Ambient fish = findAmbient(helper, home, now);
        FishCatchSavedData data = live(helper);
        UUID key = data.resolvePlayerKey(player);

        Vec3 target = center(home);
        aim(player, target.add(40, 0, 0), target, false);
        Result result = FishObservation.handle(player, fish.packet(), now);
        helper.assertTrue(result == Result.RECORDED, "a derivable fish in range and in the cone is recorded, got " + result);
        helper.assertTrue(data.hasObserved(key, fish.species()), "its species is observed");
        helper.assertTrue(FishObservation.handle(player, fish.packet(), now + ObservationRules.MIN_INTERVAL_TICKS) == Result.ALREADY_KNOWN, "recorded once");
        data.clearObserved(key);
        helper.succeed();
    }

    public static void badAmbientClaimsAreRejected(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishObservation.resetForTest();
        ServerPlayer player = mockPlayer.get();
        BlockPos home = pond(helper);
        long now = nextTick();
        Ambient fish = findAmbient(helper, home, now);
        FishCatchSavedData data = live(helper);
        UUID key = data.resolvePlayerKey(player);
        Vec3 target = center(home);
        aim(player, target.add(40, 0, 0), target, false);

        // The ID is a hash of the other inputs: change any input and it no longer matches.
        Result badId = FishObservation.handle(player, ObserveFishPacket.ambient(fish.species(), fish.id() + 1,
                fish.chunkX(), fish.chunkZ(), fish.slot(), fish.index(), home), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(badId == Result.NO_SUCH_FISH, "a made-up ID is refused, got " + badId);
        Result badSlot = FishObservation.handle(player, ObserveFishPacket.ambient(fish.species(), fish.id(),
                fish.chunkX(), fish.chunkZ(), fish.slot() + 1, fish.index(), home), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(badSlot == Result.NO_SUCH_FISH, "an ID from another slot is refused, got " + badSlot);
        Result outOfRoster = FishObservation.handle(player, ObserveFishPacket.ambient(fish.species(), fish.id(),
                fish.chunkX(), fish.chunkZ(), 4096, fish.index(), home), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(outOfRoster == Result.NO_SUCH_FISH, "a slot beyond the roster is refused, got " + outOfRoster);

        // A lifespan that is not now: the same slot a few lifespans on, with its own correct ID.
        long later = fish.index() + 5;
        long laterId = AmbientRoster.fishId(EcologySeedSync.seedFor(helper.getLevel()), fish.chunkX(), fish.chunkZ(), fish.slot(), later);
        Result notNow = FishObservation.handle(player, ObserveFishPacket.ambient(fish.species(), laterId,
                fish.chunkX(), fish.chunkZ(), fish.slot(), later, home), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(notNow == Result.NO_SUCH_FISH, "a fish that is not there now is refused, got " + notNow);

        // The right fish named as a species that cannot live there.
        Result wrongSpecies = FishObservation.handle(player, ObserveFishPacket.ambient(fish.impossible(), fish.id(),
                fish.chunkX(), fish.chunkZ(), fish.slot(), fish.index(), home), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(wrongSpecies == Result.WRONG_SPECIES, "a species outside the pool is refused, got " + wrongSpecies);

        // A home cell that is not water.
        BlockPos dry = home.above(6);
        Result dryHome = FishObservation.handle(player, ObserveFishPacket.ambient(fish.species(), fish.id(),
                fish.chunkX(), fish.chunkZ(), fish.slot(), fish.index(), dry), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(dryHome == Result.NO_SUCH_FISH, "a home cell with no water is refused, got " + dryHome);

        // Far away, and looking away.
        aim(player, target.add(ObservationRules.MAX_RANGE + 60, 0, 0), target, false);
        Result far = FishObservation.handle(player, fish.packet(), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(far == Result.OUT_OF_RANGE, "a fish far away is refused, got " + far);
        aim(player, target.add(60, 0, 0), target, true);
        Result away = FishObservation.handle(player, fish.packet(), now += ObservationRules.MIN_INTERVAL_TICKS);
        helper.assertTrue(away == Result.NOT_LOOKING, "looking the other way is refused, got " + away);

        helper.assertTrue(data.getObserved(key).isEmpty(), "none of the refused claims recorded anything");
        helper.succeed();
    }

    // ------------------------------------------------------------------ rate limit

    public static void observationPacketsAreRateLimited(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishObservation.resetForTest();
        ServerPlayer player = mockPlayer.get();
        ObserveFishPacket junk = ObserveFishPacket.visit(BLUEGILL, UUID.randomUUID(), UUID.randomUUID());
        long t = nextTick();
        helper.assertTrue(FishObservation.handle(player, junk, t) == Result.NO_SUCH_FISH, "the first packet is judged");
        helper.assertTrue(FishObservation.handle(player, junk, t + 1) == Result.RATE_LIMITED, "the next tick is dropped");
        helper.assertTrue(FishObservation.handle(player, junk, t + ObservationRules.MIN_INTERVAL_TICKS - 1) == Result.RATE_LIMITED,
                "just inside the interval is dropped");
        helper.assertTrue(FishObservation.handle(player, junk, t + ObservationRules.MIN_INTERVAL_TICKS) == Result.NO_SUCH_FISH,
                "after the interval a packet is judged again");

        // The limit is per player.
        ServerPlayer other = mockPlayer.get();
        helper.assertTrue(FishObservation.handle(other, junk, t + 1) == Result.NO_SUCH_FISH, "another player has their own budget");

        // And it applies to a valid claim too: a hit straight after a miss is dropped, not recorded.
        BlockPos pond = pond(helper);
        FishSchoolSavedData.School school = visitIn(helper, pond, BLUEGILL);
        try {
            Vec3 fish = center(pond);
            aim(other, fish.add(12, 0, 0), fish, false);
            helper.assertTrue(FishObservation.handle(other, visitPacket(school), t + 2) == Result.RATE_LIMITED,
                    "a valid claim inside the interval is dropped");
            helper.assertTrue(!live(helper).hasObserved(live(helper).resolvePlayerKey(other), BLUEGILL), "and records nothing");
        } finally {
            forget(helper, school);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ what an observation grants

    public static void observingGrantsNoCatchCountQuestProgressOrReward(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        try {
            BackupTestFixture.run(helper, () -> {
                FishObservation.resetForTest();
                ServerPlayer player = mockPlayer.get();
                FishCatchSavedData data = live(helper);
                UUID key = data.resolvePlayerKey(player);
                var quests = data.getOrCreateQuestState(player);
                var progressBefore = Map.copyOf(quests.getProgressSnapshot());
                int tokensBefore = quests.getTokenBalance();
                int claimedBefore = quests.getClaimedEncyclopediaRewardsSnapshot().size();
                int purchasesBefore = quests.getPurchaseCountSnapshot().size();
                int inventoryBefore = countItems(player);

                helper.assertTrue(FishObservation.grant(player, data, BLUEGILL), "the grant is new");
                helper.assertTrue(data.hasObserved(key, BLUEGILL), "observed");

                helper.assertTrue(data.getCatchCount(key, BLUEGILL) == 0, "no catch count");
                helper.assertTrue(data.getTotalCatchCount(key) == 0, "no lifetime catches");
                helper.assertTrue(data.getPersonalCatchCounts(key, FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC).isEmpty(),
                        "no encyclopedia catch row");
                helper.assertTrue(data.getPersonalBestSizes(key, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC).isEmpty(), "no best size");
                helper.assertTrue(data.getGlobalCatchCounts(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC).stream()
                        .noneMatch(e -> e.playerUuid().equals(key)), "not on the catch leaderboard");
                helper.assertTrue(quests.getProgressSnapshot().equals(progressBefore), "no quest progress");
                helper.assertTrue(quests.getTokenBalance() == tokensBefore, "no tokens");
                helper.assertTrue(quests.getClaimedEncyclopediaRewardsSnapshot().size() == claimedBefore, "no encyclopedia coin claimed");
                helper.assertTrue(quests.getPurchaseCountSnapshot().size() == purchasesBefore, "no shop change");
                helper.assertTrue(countItems(player) == inventoryBefore, "nothing in the inventory");

                var server = helper.getLevel().getServer();
                int reset = server.getCommands().getDispatcher().execute("fishtastic encyclopedia reset",
                        server.createCommandSourceStack().withEntity(player).withSuppressedOutput());
                helper.assertTrue(reset == 1, "the registered encyclopedia reset command succeeds");
                helper.assertTrue(data.getObserved(key).isEmpty(), "encyclopedia reset also clears spyglass observations");
                var snapshots = FishCatchBackups.list(server);
                helper.assertTrue(snapshots.size() == 1 && snapshots.get(0).kind() == FishCatchBackups.Kind.PRE_COMMAND
                                && "encyclopedia_reset".equals(snapshots.get(0).label()),
                        "the reset command writes its labelled pre-command backup");
                helper.assertTrue(FishCatchBackups.load(snapshots.get(0)).hasObserved(key, BLUEGILL),
                        "the backup preserves the observation from before reset");
            });
        } catch (Exception e) {
            throw new AssertionError("observation and encyclopedia reset check failed", e);
        }
        helper.succeed();
    }

    private static int countItems(ServerPlayer player) {
        int n = 0;
        for (var stack : player.getInventory().getNonEquipmentItems()) n += stack.getCount();
        return n;
    }

    // ------------------------------------------------------------------ the encyclopedia

    /** An observed species opens name, portrait and habitat; a catch opens the same; neither opens more. */
    public static void observationAndACatchOpenTheSameFields(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceKey<FishProfile> bluegill = ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, BLUEGILL);
        try {
            FishEncyclopediaClientCache.reset();
            FishPageModel unknown = FishPageModel.build(level.registryAccess(), bluegill);
            helper.assertTrue(unknown != null && !unknown.nameRevealed() && !unknown.discovered(), "unknown: hidden name, silhouette");

            FishEncyclopediaClientCache.update(Map.of(), List.of(), List.of(), List.of(), List.of(BLUEGILL), List.of());
            FishPageModel seen = FishPageModel.build(level.registryAccess(), bluegill);
            helper.assertTrue(seen.observed() && !seen.caught(), "observed, not caught");
            helper.assertTrue(seen.nameRevealed() && seen.discovered(), "observed: name and portrait open");
            helper.assertTrue(seen.displayName().getString().equals(seen.stack().getHoverName().getString()), "observed: the real name");
            helper.assertTrue(!seen.sizeUnlocked() && !seen.baitUnlocked() && !seen.habitsUnlocked() && !seen.loreUnlocked(),
                    "observed: size, bait, conditions and lore still need catches");
            helper.assertTrue(FishEncyclopediaClientCache.isDiscovered(BLUEGILL), "the disc and quest log treat it as discovered");

            FishEncyclopediaClientCache.update(new FishEncyclopediaSyncPacket(Map.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of()));
            FishPageModel reset = FishPageModel.build(level.registryAccess(), bluegill);
            helper.assertTrue(!FishEncyclopediaClientCache.isObserved(BLUEGILL)
                            && !reset.discovered() && !reset.nameRevealed(),
                    "a sync with the reset observed set clears the known name and portrait");

            FishEncyclopediaClientCache.update(Map.of(BLUEGILL, 1), List.of(), List.of(), List.of());
            FishPageModel caught = FishPageModel.build(level.registryAccess(), bluegill);
            helper.assertTrue(caught.caught() && !caught.observed(), "caught, not observed");
            helper.assertTrue(caught.nameRevealed() && caught.discovered(), "one catch opens the same name and portrait");
            helper.assertTrue(caught.zones().equals(seen.zones()), "and the same broad habitat");
        } finally {
            FishEncyclopediaClientCache.reset();
        }
        helper.succeed();
    }

    /** The first catch of every species opens its name, whichever rarity tier it is in. */
    public static void everyTierRevealsTheNameOnTheFirstCatch(GameTestHelper helper) {
        for (EncyclopediaTiers.Tier tier : EncyclopediaTiers.Tier.values()) {
            helper.assertTrue(tier.thresholds.nameRevealCatches() == 1, tier + " must reveal the name at one catch");
            helper.assertTrue(FishPageModel.nameRevealed(false, 1, tier.thresholds.nameRevealCatches(), false),
                    tier + ": one catch reveals the name");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ packet codec

    public static void observePacketsRoundTrip(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ObserveFishPacket visit = ObserveFishPacket.visit(BLUEGILL, UUID.randomUUID(), UUID.randomUUID());
        ObserveFishPacket.STREAM_CODEC.encode(buf, visit);
        helper.assertTrue(ObserveFishPacket.STREAM_CODEC.decode(buf).equals(visit), "a visit claim round-trips");
        helper.assertTrue(buf.readableBytes() == 0, "a visit claim leaves nothing unread");

        ObserveFishPacket ambient = ObserveFishPacket.ambient(BLUEGILL, -0x1234_5678_9ABCL, -7, 12, 5, -3L, new BlockPos(-100, 62, 200));
        ObserveFishPacket.STREAM_CODEC.encode(buf, ambient);
        helper.assertTrue(ObserveFishPacket.STREAM_CODEC.decode(buf).equals(ambient), "an ambient claim round-trips");
        helper.assertTrue(buf.readableBytes() == 0, "an ambient claim leaves nothing unread");
        helper.succeed();
    }

    // ------------------------------------------------------------------ persistence

    /** Save and reload (the saved-data codec), then a manual backup, a change, and a player and whole-server restore. */
    public static void observedSetSurvivesSaveReloadAndBackupRestore(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) throws IOException {
        var server = helper.getLevel().getServer();
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
        ServerPlayer player = mockPlayer.get();
        UUID key = data.resolvePlayerKey(player);
        Identifier trout = Ids.of("fishtastic", "trout");
        data.recordObservation(key, BLUEGILL);
        data.recordObservation(key, trout);

        // Save and reload.
        Object encoded = FishCatchSavedData.CODEC.encodeStart(JavaOps.INSTANCE, data)
                .getOrThrow(msg -> new IllegalStateException("encode: " + msg));
        FishCatchSavedData reloaded = FishCatchSavedData.CODEC.parse(JavaOps.INSTANCE, encoded)
                .getOrThrow(msg -> new IllegalStateException("decode: " + msg));
        helper.assertTrue(reloaded.getObserved(key).equals(java.util.Set.of(BLUEGILL, trout)), "the observed set reloads");
        helper.assertTrue(reloaded.hasPlayer(key), "an observer is a known player even with no catches");

        // Backup, change, restore the player.
        var snapshot = FishCatchBackups.create(server, FishCatchBackups.Kind.MANUAL, "gt_observed").orElseThrow();
        try {
            helper.assertTrue(FishCatchBackups.load(snapshot).getObserved(key).equals(java.util.Set.of(BLUEGILL, trout)),
                    "the backup holds the observed set");
            data.clearObserved(key);
            data.recordObservation(key, Ids.of("fishtastic", "pike"));
            helper.assertTrue(!data.hasObserved(key, BLUEGILL), "setup: changed after the backup");

            FishCatchBackups.restorePlayer(server, snapshot, key);
            helper.assertTrue(data.getObserved(key).equals(java.util.Set.of(BLUEGILL, trout)), "restore-player brings the set back");

            data.clearObserved(key);
            FishCatchBackups.restoreAll(server, snapshot);
            helper.assertTrue(FishCatchSavedData.getOrCreate(server).getObserved(key).equals(java.util.Set.of(BLUEGILL, trout)),
                    "restore-all brings the set back");
        } finally {
            FishCatchBackups.delete(snapshot);
            FishCatchSavedData.getOrCreate(server).clearObserved(key);
        }
        helper.succeed();
    }
}
