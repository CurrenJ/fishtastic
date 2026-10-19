package grill24.fishtastic.server;

import grill24.fishtastic.network.FishtasticTransport;
import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.network.AmbientContextRequestPacket;
import grill24.fishtastic.network.AmbientContextResponsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the ambient context exchange (Fish in World, Phase 4): answers
 * {@link AmbientContextRequestPacket} with authoritative species contexts derived from the server's
 * own equipment and world state via {@link FishingPoolResolver}.
 *
 * <h2>Validation order — nothing reads world state first</h2>
 * For every requested origin, in this order, before any world read:
 * <ol>
 *   <li><b>Dimension</b> — must be the player's current dimension.</li>
 *   <li><b>Distance</b> — within {@link #MAX_ORIGIN_HORIZONTAL_DISTANCE} blocks horizontally and
 *       {@link #MAX_ORIGIN_VERTICAL_DISTANCE} vertically of the player. The client's own discovery
 *       window is smaller than this, so a legitimate client is never rejected; a forged request
 *       cannot use the endpoint to sweep arbitrary loaded regions.</li>
 *   <li><b>Loaded</b> — {@code level.isLoaded(pos)} (a {@code hasChunk} check). The server never
 *       loads or generates a chunk to answer. Every later read ({@code getFluidState} — which goes
 *       through {@code getChunkAt} — and the resolver's biome/weather/moon lookups) is safe because
 *       the chunk is already loaded.</li>
 *   <li><b>Water</b> — the origin cell must hold water <em>now</em>; the client's view may be
 *       stale, and a stale origin is rejected, not resolved somewhere else.</li>
 * </ol>
 * Only then is anything resolved, and resolution happens on the main thread: the packet handler
 * enqueues work, and {@link #buildResponse} assumes it is on the server thread.
 *
 * <h2>Rate limit</h2>
 * One {@link AmbientRequestThrottle} per player (see that class for the numbers). A throttled
 * request is answered with nothing at all — "no response" is a supported client state. The throttle
 * is the only per-player state here; it is dropped after {@link AmbientRequestThrottle#IDLE_EXPIRY_TICKS}
 * of inactivity, and the table is pruned before every insert so it can never grow past
 * {@link #MAX_TRACKED_PLAYERS}: a crowded table first drops idle buckets, then evicts the
 * least-recently-used one. It is never cleared wholesale, so an active player's partially spent
 * burst survives a stranger arriving at the cap.
 *
 * <h2>Session snapshot precedence</h2>
 * An active fishing session's captured {@link FishingPoolResolver.SessionSnapshot} is served for
 * origins within {@link #SESSION_SNAPSHOT_RADIUS} blocks of its origin (and
 * {@link #SESSION_SNAPSHOT_VERTICAL} vertically) — a near-exact match, not a discovery window.
 * The server has no water-patch identity (patches exist only in the client's discovery), so
 * "same patch" is approximated by proximity to the cast: the snapshot's origin is the hook's cast
 * position, and a client's origin for that water is one surface cell inside the same body, so a
 * few blocks of slack covers the sample-cell-to-hook spread. An origin beyond that — even on a
 * neighbouring pond — falls through to the normal fresh resolve, which is still authoritative;
 * it just does not carry the session's captured value.
 */
public final class AmbientContextServer {

    /** Mirrors the client's discovery window (radius 24 + evict slack 8) with headroom. */
    public static final double MAX_ORIGIN_HORIZONTAL_DISTANCE = 64d;
    public static final double MAX_ORIGIN_VERTICAL_DISTANCE = 64d;
    /**
     * Chebyshev horizontal / vertical radius around a session snapshot's origin — a near-exact
     * match, not a discovery window. The snapshot's origin is the hook's cast position; a client's
     * origin for the same patch is one surface cell of the same water, a few blocks away at most,
     * while two neighbouring ponds (the fixtures sit 16 apart) must never share a snapshot.
     */
    public static final double SESSION_SNAPSHOT_RADIUS = 4d;
    public static final double SESSION_SNAPSHOT_VERTICAL = 8d;
    static final int MAX_TRACKED_PLAYERS = 512;

    private static final Map<UUID, AmbientRequestThrottle> THROTTLES = new HashMap<>();

    private static long responsesSent;
    private static long originsResolved;
    private static long originsRejected;

    private AmbientContextServer() {
    }

    /** Handles one request on the server thread: build the answer, send it, log it. */
    public static void handle(ServerPlayer player, AmbientContextRequestPacket packet) {
        long tick = player.level().getGameTime();
        AmbientContextResponsePacket response = buildResponse(player, packet, tick);
        if (response == null || response.entries().isEmpty()) {
            return; // throttled, or nothing answerable: silently no response, as documented
        }
        FishtasticTransport.sendToPlayer(player, response);
        responsesSent++;
        if (responsesSent <= 10 || responsesSent % 100 == 0) {
            StringBuilder summary = new StringBuilder(96);
            for (AmbientContextResponsePacket.Entry entry : response.entries()) {
                if (entry.status() == AmbientContextResponsePacket.Status.OK) {
                    summary.append(" [key=").append(entry.patchKey())
                            .append(" fp=").append(Long.toHexString(entry.fingerprint()))
                            .append(" n=").append(entry.candidates().size())
                            .append(" mode=").append(entry.sessionSourced() ? "session" : entry.heldRod() ? "rod" : "worms")
                            .append(entry.truncated() ? " truncated" : "")
                            .append(']');
                } else {
                    summary.append(" [key=").append(entry.patchKey()).append(' ').append(entry.status()).append(']');
                }
            }
            Fishtastic.LOGGER.info("Ambient context response #{} to {} (session={}, reqGen={}):{}",
                    responsesSent, player.getName().getString(), packet.session(),
                    packet.requestGeneration(), summary);
        } else {
            Fishtastic.LOGGER.debug("Ambient context response #{} to {}: {} entries",
                    responsesSent, player.getName().getString(), response.entries().size());
        }
    }

    /**
     * Builds the response for one request without sending it. Public and tick-parameterised so
     * GameTests can drive it deterministically.
     *
     * @return the response, or null when the request is rate-limited (or has no origins at all)
     * @implNote must run on the server thread
     */
    @Nullable
    public static AmbientContextResponsePacket buildResponse(ServerPlayer player,
                                                             AmbientContextRequestPacket packet,
                                                             long tick) {
        // Nothing answerable costs nothing, and an empty request is not a request: check before the
        // throttle so a no-op can never spend a token or disturb the tracked-player table.
        List<AmbientContextRequestPacket.Origin> origins = packet.origins();
        if (origins.isEmpty()) {
            return null;
        }
        if (origins.size() > AmbientContextRequestPacket.MAX_ORIGINS) {
            // The codec bounds the list already; clamp again for any non-codec caller. Bounded
            // work beats rejecting outright, and the client only ever asks about MAX_ORIGINS.
            origins = origins.subList(0, AmbientContextRequestPacket.MAX_ORIGINS);
        }

        AmbientRequestThrottle throttle = throttleFor(player.getUUID(), tick);
        if (!throttle.tryAcquire(tick)) {
            long dropped = throttle.dropped();
            if (Long.bitCount(dropped) == 1) { // 1, 2, 4, 8, ...: bounded log volume
                Fishtastic.LOGGER.info("Ambient context request from {} dropped by rate limit ({} dropped so far)",
                        player.getName().getString(), dropped);
            } else {
                Fishtastic.LOGGER.debug("Ambient context request from {} dropped by rate limit", player.getName().getString());
            }
            return null;
        }

        ServerLevel level = player.level();
        Registry<Item> itemRegistry = level.registryAccess().lookupOrThrow(Registries.ITEM);
        Registry<FishProfile> profileRegistry =
                level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        // One snapshot read for the whole request: all origins share the player's session.
        FishingPoolResolver.SessionSnapshot snapshot =
                FishingMinigameManager.get(level).getSessionSnapshot(player.getUUID());

        List<AmbientContextResponsePacket.Entry> entries = new ArrayList<>(origins.size());
        for (AmbientContextRequestPacket.Origin origin : origins) {
            entries.add(resolveOrigin(player, level, itemRegistry, profileRegistry, snapshot, origin));
        }
        return new AmbientContextResponsePacket(packet.session(), packet.requestGeneration(), entries);
    }

    private static AmbientContextResponsePacket.Entry resolveOrigin(
            ServerPlayer player, ServerLevel level,
            Registry<Item> itemRegistry, Registry<FishProfile> profileRegistry,
            @Nullable FishingPoolResolver.SessionSnapshot snapshot,
            AmbientContextRequestPacket.Origin origin) {
        AmbientContextResponsePacket.Status rejection = validate(player, level, origin);
        if (rejection != null) {
            originsRejected++;
            return AmbientContextResponsePacket.Entry.rejected(origin.patchKey(),
                    origin.geometryGeneration(), origin.contextGeneration(), rejection);
        }

        BlockPos pos = origin.pos();
        if (snapshot != null && nearSnapshot(snapshot.origin(), pos)) {
            FishingPoolResolver.SpeciesContext species = snapshot.species();
            Candidates capped = toCandidates(species.candidates());
            originsResolved++;
            return new AmbientContextResponsePacket.Entry(origin.patchKey(), origin.geometryGeneration(),
                    origin.contextGeneration(), AmbientContextResponsePacket.Status.OK,
                    true, snapshot.rodId() != null, capped.truncated(), species.fingerprint(), capped.list());
        }

        // The authoritative fresh resolve: the server's own equipment (the same main-hand-first rod
        // rule real casting uses) and the server's own world at the validated origin.
        ItemStack rod = FishingMinigameManager.findFishtasticRod(player);
        FishingPoolResolver.Inputs inputs = rod.isEmpty()
                ? FishingPoolResolver.Inputs.standardWormsBaseline()
                : FishingPoolResolver.Inputs.heldRodPreview(rod, player);
        FishingPoolResolver.EnvironmentContext environment =
                FishingPoolResolver.resolveEnvironment(level, pos, inputs.charmEffect());
        FishingPoolResolver.SpeciesContext species =
                FishingPoolResolver.resolve(itemRegistry, profileRegistry, environment, inputs);
        Candidates capped = toCandidates(species.candidates());
        originsResolved++;
        return new AmbientContextResponsePacket.Entry(origin.patchKey(), origin.geometryGeneration(),
                origin.contextGeneration(), AmbientContextResponsePacket.Status.OK,
                false, !rod.isEmpty(), capped.truncated(), species.fingerprint(), capped.list());
    }

    /** The first validation failure for an origin, or null when it passes every check. */
    @Nullable
    private static AmbientContextResponsePacket.Status validate(ServerPlayer player, ServerLevel level,
                                                                AmbientContextRequestPacket.Origin origin) {
        if (!origin.dimension().equals(level.dimension().identifier())) {
            return AmbientContextResponsePacket.Status.REJECTED_DIMENSION;
        }
        BlockPos pos = origin.pos();
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        double dy = pos.getY() + 0.5 - player.getY();
        if (dx * dx + dz * dz > MAX_ORIGIN_HORIZONTAL_DISTANCE * MAX_ORIGIN_HORIZONTAL_DISTANCE
                || Math.abs(dy) > MAX_ORIGIN_VERTICAL_DISTANCE) {
            return AmbientContextResponsePacket.Status.REJECTED_FAR;
        }
        // The load check must stay the first world read: getFluidState below goes through
        // Level#getChunkAt, which forces chunk load — safe only because isLoaded gated it.
        if (!level.isLoaded(pos)) {
            return AmbientContextResponsePacket.Status.REJECTED_UNLOADED;
        }
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return AmbientContextResponsePacket.Status.REJECTED_NOT_WATER;
        }
        return null;
    }

    private static boolean nearSnapshot(Vec3 snapshotOrigin, BlockPos pos) {
        double dx = snapshotOrigin.x - (pos.getX() + 0.5);
        double dz = snapshotOrigin.z - (pos.getZ() + 0.5);
        double dy = snapshotOrigin.y - (pos.getY() + 0.5);
        return Math.abs(dx) <= SESSION_SNAPSHOT_RADIUS && Math.abs(dz) <= SESSION_SNAPSHOT_RADIUS
                && Math.abs(dy) <= SESSION_SNAPSHOT_VERTICAL;
    }

    /** The candidate list, capped to the wire bound by dropping the lowest weights (stable). */
    private record Candidates(List<AmbientContextResponsePacket.Candidate> list, boolean truncated) {
    }

    private static Candidates toCandidates(List<FishingPoolResolver.WeightedFish> weighted) {
        List<AmbientContextResponsePacket.Candidate> all = new ArrayList<>(weighted.size());
        for (FishingPoolResolver.WeightedFish fish : weighted) {
            var key = fish.item().unwrapKey();
            if (key.isEmpty()) continue; // no id to send: an unregistered holder cannot be requested
            all.add(new AmbientContextResponsePacket.Candidate(key.get().identifier(), fish.weight()));
        }
        if (all.size() <= AmbientContextResponsePacket.MAX_CANDIDATES) {
            return new Candidates(List.copyOf(all), false);
        }
        int[] weights = new int[all.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = all.get(i).weight();
        int[] keep = topWeightIndices(weights, AmbientContextResponsePacket.MAX_CANDIDATES);
        List<AmbientContextResponsePacket.Candidate> kept = new ArrayList<>(keep.length);
        for (int i : keep) kept.add(all.get(i));
        return new Candidates(List.copyOf(kept), true);
    }

    /**
     * The indices of the {@code max} highest weights, ascending (pool order among the survivors) so
     * truncation keeps the resolver's ordering guarantee. Ties keep pool order in the ranking too.
     * Pure and package-visible: the truncation policy is pinned by a JUnit test, not by building a
     * pool larger than the wire cap in a running world.
     */
    static int[] topWeightIndices(int[] weights, int max) {
        int n = weights.length;
        if (max >= n) {
            int[] all = new int[n];
            for (int i = 0; i < n; i++) all[i] = i;
            return all;
        }
        List<Integer> ranked = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ranked.add(i);
        ranked.sort((a, b) -> {
            int byWeight = Integer.compare(weights[b], weights[a]);
            return byWeight != 0 ? byWeight : Integer.compare(a, b);
        });
        int[] keep = new int[max];
        for (int i = 0; i < max; i++) keep[i] = ranked.get(i);
        java.util.Arrays.sort(keep);
        return keep;
    }

    static AmbientRequestThrottle throttleFor(UUID playerId, long tick) {
        pruneIfCrowded(playerId, tick);
        return THROTTLES.computeIfAbsent(playerId, id -> new AmbientRequestThrottle());
    }

    /** How many per-player buckets are tracked; the bound is pinned by a JUnit test. */
    static int trackedThrottleCount() {
        return THROTTLES.size();
    }

    /**
     * Makes room for {@code incoming} before it is inserted: at the cap, first forget idle buckets,
     * then evict the least-recently-used one. The table never holds more than
     * {@link #MAX_TRACKED_PLAYERS} buckets, and it is never cleared wholesale — a live player's
     * partially spent burst must survive strangers arriving at the cap.
     */
    private static void pruneIfCrowded(UUID incoming, long tick) {
        if (THROTTLES.size() < MAX_TRACKED_PLAYERS || THROTTLES.containsKey(incoming)) {
            // An already-tracked player does not grow the table; nobody needs evicting for them.
            return;
        }
        THROTTLES.values().removeIf(throttle -> throttle.idleExpired(tick));
        if (THROTTLES.size() >= MAX_TRACKED_PLAYERS) {
            UUID eldest = null;
            long eldestUse = Long.MAX_VALUE;
            for (Map.Entry<UUID, AmbientRequestThrottle> entry : THROTTLES.entrySet()) {
                long use = entry.getValue().lastUseTick();
                if (use < eldestUse) {
                    eldestUse = use;
                    eldest = entry.getKey();
                }
            }
            if (eldest != null) {
                THROTTLES.remove(eldest);
            }
        }
    }

    // ------------------------------------------------------------------ test seams

    /** Clears the per-player throttle state and counters. GameTests need a deterministic start. */
    public static void resetForTest() {
        THROTTLES.clear();
        responsesSent = 0;
        originsResolved = 0;
        originsRejected = 0;
    }

    public static long responsesSent() {
        return responsesSent;
    }

    public static long originsResolved() {
        return originsResolved;
    }

    public static long originsRejected() {
        return originsRejected;
    }
}
