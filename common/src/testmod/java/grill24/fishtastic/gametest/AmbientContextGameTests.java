package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.network.AmbientContextRequestPacket;
import grill24.fishtastic.network.AmbientContextResponsePacket;
import grill24.fishtastic.server.AmbientContextServer;
import grill24.fishtastic.server.AmbientRequestThrottle;
import grill24.fishtastic.server.FishingMinigameManager;
import grill24.fishtastic.server.FishingPoolResolver;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server-side game tests for the ambient context exchange (Fish in World, Phase 4): the validation
 * order, the authoritative resolve, the session-snapshot precedence and the per-player rate limit,
 * all driven through {@link AmbientContextServer#buildResponse} so no client is involved.
 *
 * <p>Every test relocates its mock player into the test structure: mock players are created at the
 * literal world origin (see FishingMinigameManagerGameTests), and the origins under test are cells
 * of this structure — the server's distance check applies to the player, so the two must coincide.
 *
 * <p>The unloaded-origin test is the interesting one: it moves the player far from any generated
 * chunk and proves both that {@code REJECTED_UNLOADED} is returned and that answering did not load
 * the chunk (before/after {@code hasChunk} checks). A distance rejection is checked first to prove
 * the order of the two checks.
 *
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class AmbientContextGameTests {

    private AmbientContextGameTests() {}

    private static final long PATCH_KEY = 4242L;
    private static final int GEOMETRY_GENERATION = 3;
    private static final int CONTEXT_GENERATION = 1;
    /** A structure cell that no other fixture uses; water is placed here. */
    private static final BlockPos WATER = new BlockPos(2, 1, 1);

    /** Mock players land at each loader's mock position; origins must be measured from it. */
    private static void relocateIntoTestStructure(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static AmbientContextRequestPacket.Origin originAt(ServerPlayer player, BlockPos pos) {
        return new AmbientContextRequestPacket.Origin(
                player.level().dimension().location(),
                PATCH_KEY, GEOMETRY_GENERATION, CONTEXT_GENERATION, pos);
    }

    private static AmbientContextRequestPacket requestFor(AmbientContextRequestPacket.Origin... origins) {
        return new AmbientContextRequestPacket(0x5E5510ABC01L, 1, List.of(origins));
    }

    /** One response entry for a single-origin request against a water cell in this structure. */
    private static AmbientContextResponsePacket.Entry answerOneOrigin(GameTestHelper helper,
                                                                     ServerPlayer player, BlockPos origin) {
        AmbientContextResponsePacket response = AmbientContextServer.buildResponse(
                player, requestFor(originAt(player, origin)), helper.getLevel().getGameTime());
        helper.assertTrue(response != null, "Sanity check: the first request of a test must not be rate-limited");
        helper.assertTrue(response.entries().size() == 1, "One origin must produce exactly one entry");
        return response.entries().get(0);
    }

    // -------------------------------------------------------------------------
    // The authoritative resolve: the server's own equipment and world state
    // -------------------------------------------------------------------------

    /** No rod held: the answer is the standard-worms baseline resolved at the origin. */
    public static void authoritativeResolveMatchesServerState(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.setBlock(WATER, Blocks.WATER);
        AmbientContextServer.resetForTest();

        BlockPos origin = helper.absolutePos(WATER);
        // Empty hands: the mock player's equipment lane is the no-rod standard-worms baseline.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        AmbientContextResponsePacket.Entry entry = answerOneOrigin(helper, player, origin);

        helper.assertTrue(entry.status() == AmbientContextResponsePacket.Status.OK,
                "Water within the discovery window must resolve, got " + entry.status());
        helper.assertTrue(!entry.sessionSourced(), "No session is active, so the answer cannot be session-sourced");
        helper.assertTrue(!entry.heldRod(), "No rod is held, so the answer must be the no-rod baseline");
        helper.assertTrue(entry.patchKey() == PATCH_KEY
                        && entry.geometryGeneration() == GEOMETRY_GENERATION
                        && entry.contextGeneration() == CONTEXT_GENERATION,
                "The entry must echo the origin's tokens exactly");
        helper.assertTrue(!entry.truncated(), "The real pool is far below the wire cap, nothing may be truncated");

        FishingPoolResolver.SpeciesContext expected = FishingPoolResolver.resolve(
                helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM),
                helper.getLevel().registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY),
                FishingPoolResolver.resolveEnvironment(helper.getLevel(), origin, null),
                FishingPoolResolver.Inputs.standardWormsBaseline());
        assertMatchesSpecies(helper, entry, expected, "the no-rod baseline resolve at the origin");
        helper.succeed();
    }

    /** A held rod switches the equipment lane to the held-rod preview, from the server's own state. */
    public static void heldRodLaneIsResolvedFromTheServersOwnInventory(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.setBlock(WATER, Blocks.WATER);
        AmbientContextServer.resetForTest();

        ItemStack rod = new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        BlockPos origin = helper.absolutePos(WATER);

        AmbientContextResponsePacket.Entry entry = answerOneOrigin(helper, player, origin);

        helper.assertTrue(entry.status() == AmbientContextResponsePacket.Status.OK,
                "A held rod must still resolve, got " + entry.status());
        helper.assertTrue(entry.heldRod(), "The entry must record that a supported rod was held");

        FishingPoolResolver.SpeciesContext expected = FishingPoolResolver.resolve(
                helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM),
                helper.getLevel().registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY),
                FishingPoolResolver.resolveEnvironment(helper.getLevel(), origin, null),
                FishingPoolResolver.Inputs.heldRodPreview(rod, player));
        assertMatchesSpecies(helper, entry, expected, "the held-rod preview resolve");
        helper.succeed();
    }

    private static void assertMatchesSpecies(GameTestHelper helper, AmbientContextResponsePacket.Entry entry,
                                             FishingPoolResolver.SpeciesContext expected, String label) {
        helper.assertTrue(entry.fingerprint() == expected.fingerprint(),
                "The answer must carry the fingerprint of " + label + ": expected "
                        + Long.toHexString(expected.fingerprint()) + " got " + Long.toHexString(entry.fingerprint()));
        List<AmbientContextResponsePacket.Candidate> expectedCandidates = new ArrayList<>();
        for (FishingPoolResolver.WeightedFish fish : expected.candidates()) {
            var key = fish.item().unwrapKey();
            helper.assertTrue(key.isPresent(), "Every pool candidate must be a registered item");
            expectedCandidates.add(new AmbientContextResponsePacket.Candidate(key.get().location(), fish.weight()));
        }
        helper.assertTrue(entry.candidates().equals(expectedCandidates),
                "The candidate list and weights must equal " + label + " in pool order");
    }

    // -------------------------------------------------------------------------
    // Validation order: dimension, distance, loaded, water
    // -------------------------------------------------------------------------

    /** A foreign dimension is rejected before anything else — even with water placed ready. */
    public static void dimensionMismatchIsRejected(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.setBlock(WATER, Blocks.WATER);
        AmbientContextServer.resetForTest();

        BlockPos origin = helper.absolutePos(WATER);
        AmbientContextRequestPacket.Origin wrongDimension = new AmbientContextRequestPacket.Origin(
                Ids.of("minecraft", "the_nether"),
                PATCH_KEY, GEOMETRY_GENERATION, CONTEXT_GENERATION, origin);
        AmbientContextResponsePacket response = AmbientContextServer.buildResponse(
                player, requestFor(wrongDimension), helper.getLevel().getGameTime());

        helper.assertTrue(response != null && response.entries().size() == 1,
                "A dimension rejection is an answer, not a dropped request");
        helper.assertTrue(response.entries().get(0).status() == AmbientContextResponsePacket.Status.REJECTED_DIMENSION,
                "An origin in another dimension must be rejected with REJECTED_DIMENSION, got "
                        + response.entries().get(0).status());
        helper.assertTrue(response.entries().get(0).candidates().isEmpty(),
                "A rejection must carry no candidates");
        helper.succeed();
    }

    /**
     * A far origin is rejected as far even though its chunk is certainly unloaded too — proving the
     * distance check runs before the load check (an unloaded-first order would answer UNLOADED).
     */
    public static void farOriginIsRejectedBeforeTheUnloadedCheck(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        AmbientContextServer.resetForTest();

        BlockPos far = player.blockPosition().offset(2000, 0, 0);
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4),
                "Sanity check: the far origin's chunk must not be loaded");

        AmbientContextResponsePacket.Entry entry = answerOneOrigin(helper, player, far);
        helper.assertTrue(entry.status() == AmbientContextResponsePacket.Status.REJECTED_FAR,
                "An origin that far outside the discovery window must be REJECTED_FAR, got " + entry.status());
        helper.succeed();
    }

    /**
     * The core "never load a chunk to answer" test: the player is moved far from every generated
     * chunk, and the answer at a nearby origin is REJECTED_UNLOADED with the chunk still unloaded
     * before and after — nothing in this exchange may force-load or generate.
     */
    public static void unloadedOriginIsRejectedWithoutLoadingTheChunk(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        // Far beyond anything any other test or the spawn logic generates.
        player.setPos(100000.5, player.getY(), 100000.5);
        AmbientContextServer.resetForTest();

        // Find an unloaded chunk within the discovery window (the player's own neighbourhood out
        // here is unloaded with certainty; the scan keeps the test honest if that ever changes).
        BlockPos origin = null;
        int[] offsets = {8, 0, -8};
        outer:
        for (int dx : offsets) {
            for (int dz : offsets) {
                BlockPos candidate = player.blockPosition().offset(dx, 0, dz);
                if (!helper.getLevel().getChunkSource().hasChunk(candidate.getX() >> 4, candidate.getZ() >> 4)) {
                    origin = candidate;
                    break outer;
                }
            }
        }
        helper.assertTrue(origin != null,
                "Expected at least one unloaded chunk within the discovery window at x=100000 — "
                        + "the gametest world has generated chunks out here");

        AmbientContextResponsePacket.Entry entry = answerOneOrigin(helper, player, origin);

        helper.assertTrue(entry.status() == AmbientContextResponsePacket.Status.REJECTED_UNLOADED,
                "An origin in an unloaded chunk must be REJECTED_UNLOADED, got " + entry.status());
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(origin.getX() >> 4, origin.getZ() >> 4),
                "Answering must never load the chunk: the origin's chunk must still be unloaded afterwards");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Rate limit
    // -------------------------------------------------------------------------

    /** The burst capacity is answered, the request past it is silently dropped, and one interval refills. */
    public static void rateLimitDropsRequestsPastTheBurst(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.setBlock(WATER, Blocks.WATER);
        AmbientContextServer.resetForTest();

        AmbientContextRequestPacket packet = requestFor(originAt(player, helper.absolutePos(WATER)));
        long tick = helper.getLevel().getGameTime();
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            helper.assertTrue(AmbientContextServer.buildResponse(player, packet, tick) != null,
                    "Request " + i + " within the burst must be answered");
        }
        helper.assertTrue(AmbientContextServer.buildResponse(player, packet, tick) == null,
                "The request past the burst must be dropped with no answer — a supported client state");
        helper.assertTrue(AmbientContextServer.buildResponse(player, packet,
                        tick + AmbientRequestThrottle.REFILL_INTERVAL_TICKS) != null,
                "One refill interval later the request must be answered again");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Active-session precedence
    // -------------------------------------------------------------------------

    /**
     * A cast captures a species snapshot at the hook; while that session lives, the snapshot — not a
     * fresh resolve from the player's current equipment — answers origins on its patch. The rod is
     * removed after the cast so the fresh lane would visibly differ if precedence were lost.
     */
    public static void sessionSnapshotTakesPrecedenceOverAFreshResolve(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.setBlock(WATER, Blocks.WATER);
        AmbientContextServer.resetForTest();

        ItemStack rod = new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        FishingHook hook = new FishingHook(player, helper.getLevel(), 0, 0);
        helper.getLevel().addFreshEntity(hook);

        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());
        int sessionId = manager.startSession(player, 1.0f, false);
        helper.assertTrue(sessionId != -1, "Sanity check: the cast must start a session");
        FishingPoolResolver.SessionSnapshot snapshot = manager.getSessionSnapshot(player.getUUID());
        helper.assertTrue(snapshot != null, "Sanity check: a real cast captures a species snapshot");

        // Equipment change after the cast: a stale response to this state must be impossible to
        // confuse with the session's answer.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        BlockPos origin = helper.absolutePos(WATER);
        AmbientContextResponsePacket.Entry entry = answerOneOrigin(helper, player, origin);

        helper.assertTrue(entry.status() == AmbientContextResponsePacket.Status.OK,
                "The origin is within the snapshot's reach and must resolve, got " + entry.status());
        helper.assertTrue(entry.sessionSourced(),
                "The active session's snapshot must answer for its patch, not a fresh resolve");
        helper.assertTrue(entry.heldRod(),
                "The entry records the equipment lane of the SESSION's cast (it had a rod), not the current hand");
        helper.assertTrue(entry.fingerprint() == snapshot.species().fingerprint(),
                "The answer must carry the session's fingerprint, expected "
                        + Long.toHexString(snapshot.species().fingerprint()) + " got "
                        + Long.toHexString(entry.fingerprint()));

        // The fresh lane (no rod now) would resolve differently: prove the answer was not that.
        FishingPoolResolver.SpeciesContext fresh = FishingPoolResolver.resolve(
                helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM),
                helper.getLevel().registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY),
                FishingPoolResolver.resolveEnvironment(helper.getLevel(), origin, null),
                FishingPoolResolver.Inputs.standardWormsBaseline());
        helper.assertTrue(fresh.fingerprint() != snapshot.species().fingerprint(),
                "Sanity check: the post-switch fresh resolve must differ from the session's snapshot, "
                        + "otherwise this test could not tell the two apart");

        manager.cancelSession(player);
        helper.succeed();
    }

    /**
     * The snapshot's reach is near-exact, not a window: a second water cell inside the old
     * 32-block radius (the fixture ponds sit 16 apart) must NOT be answered from a session that
     * cast somewhere else — it falls through to the authoritative fresh resolve.
     */
    public static void aDifferentPondNearTheCastFallsThroughToAFreshResolve(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.setBlock(WATER, Blocks.WATER);
        // A second pond, 6 blocks from the cast site: inside the old 32-block snapshot radius,
        // outside the near-exact match. Written straight to the level because it sits outside the
        // empty test structure's own bounds.
        BlockPos otherPondRel = new BlockPos(2, 1, 7);
        BlockPos otherPond = helper.absolutePos(otherPondRel);
        helper.getLevel().setBlockAndUpdate(otherPond, Blocks.WATER.defaultBlockState());
        helper.assertTrue(helper.getLevel().getBlockState(otherPond).is(Blocks.WATER),
                "Sanity check: the second pond cell must hold water");
        AmbientContextServer.resetForTest();

        ItemStack rod = new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        FishingHook hook = new FishingHook(player, helper.getLevel(), 0, 0);
        helper.getLevel().addFreshEntity(hook);

        FishingMinigameManager manager = FishingMinigameManager.get(helper.getLevel());
        helper.assertTrue(manager.startSession(player, 1.0f, false) != -1, "Sanity check: the cast must start a session");
        FishingPoolResolver.SessionSnapshot snapshot = manager.getSessionSnapshot(player.getUUID());
        helper.assertTrue(snapshot != null, "Sanity check: a real cast captures a species snapshot");

        // The rod comes off after the cast: the fresh lane (worms) would visibly differ if the
        // answer were wrongly session-sourced — and the fresh resolve is what the test asserts.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        AmbientContextResponsePacket.Entry entry = answerOneOrigin(helper, player, otherPond);
        helper.assertTrue(entry.status() == AmbientContextResponsePacket.Status.OK,
                "The second pond is loaded water and must resolve, got " + entry.status());
        helper.assertTrue(!entry.sessionSourced(),
                "A patch that far from where the cast resolved must fall through to a fresh resolve, "
                        + "not be answered from the session snapshot");

        FishingPoolResolver.SpeciesContext fresh = FishingPoolResolver.resolve(
                helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM),
                helper.getLevel().registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY),
                FishingPoolResolver.resolveEnvironment(helper.getLevel(), otherPond, null),
                FishingPoolResolver.Inputs.standardWormsBaseline());
        helper.assertTrue(entry.fingerprint() == fresh.fingerprint(),
                "The fall-through answer must be the current-equipment resolve, expected "
                        + Long.toHexString(fresh.fingerprint()) + " got " + Long.toHexString(entry.fingerprint()));
        helper.assertTrue(fresh.fingerprint() != snapshot.species().fingerprint(),
                "Sanity check: the fresh worms resolve must differ from the session's rod snapshot");

        manager.cancelSession(player);
        helper.succeed();
    }

    /**
     * An empty-origins request is answerable by nothing and must not spend a throttle token: the
     * full burst remains available afterwards.
     */
    public static void anEmptyRequestDoesNotConsumeAThrottleToken(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.setBlock(WATER, Blocks.WATER);
        AmbientContextServer.resetForTest();

        long tick = helper.getLevel().getGameTime();
        AmbientContextRequestPacket empty =
                new AmbientContextRequestPacket(0x5E5510ABC01L, 1, List.of());
        helper.assertTrue(AmbientContextServer.buildResponse(player, empty, tick) == null,
                "An empty request has nothing to answer and must produce no response");

        AmbientContextRequestPacket packet = requestFor(originAt(player, helper.absolutePos(WATER)));
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            helper.assertTrue(AmbientContextServer.buildResponse(player, packet, tick) != null,
                    "The empty request must not have spent a token: burst request " + i
                            + " of " + AmbientRequestThrottle.BURST_CAPACITY + " must still be answered");
        }
        helper.succeed();
    }
}
