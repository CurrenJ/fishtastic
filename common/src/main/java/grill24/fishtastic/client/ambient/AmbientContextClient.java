package grill24.fishtastic.client.ambient;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.AmbientContextRequestPacket;
import grill24.fishtastic.network.AmbientContextResponsePacket;
import grill24.fishtastic.server.FishingMinigameManager;
import grill24.fishtastic.server.FishingPoolResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side manager for authoritative ambient species contexts (Fish in World, Phase 4): asks the
 * server what pool applies at the water patches the ambient runtime is showing, and hands the
 * versioned answers to {@link AmbientContextStore}.
 *
 * <h2>Refresh policy — event-driven, plus one bounded periodic re-check</h2>
 * A dispatch (at most one per {@link #MIN_DISPATCH_INTERVAL_TICKS}, at most
 * {@link AmbientContextRequestPacket#MAX_ORIGINS} origins, only for the runtime's shown patches) is
 * requested when:
 * <ul>
 *   <li>the shown patch set changed, or a shown patch's geometry signature moved — both compared
 *       every tick, so a discovered pond is asked about promptly and changed water is re-asked
 *       before any stale answer could be served (the runtime already samples the signatures every
 *       tick; the compare itself is a map lookup per patch);</li>
 *   <li>the equipment lane changed (the species-affecting inputs of the held rod: bait, charm,
 *       carried diamonds — computed by the same {@link FishingPoolResolver.Inputs} helpers the
 *       server uses, so the two can't drift);</li>
 *   <li>the level/dimension changed or the store was reset (reconnect, reload);</li>
 *   <li>{@link #PERIODIC_RECHECK_TICKS} elapsed — the bounded check for server-side drift the
 *       client cannot see (time of day, weather, moon phase).</li>
 * </ul>
 * There is deliberately no per-fish or per-frame resolution anywhere: at steady state this sends
 * one small request per 10 seconds, and the runtime's shown set is the only origin source.
 *
 * <h2>Not spawning from unknown pools</h2>
 * Population consumers read
 * {@link AmbientContextStore#freshContext(long)} and must treat every non-{@code OK} state as "no
 * known pool".
 *
 * <p>Client-only. Registered from each loader's client entrypoint; a dedicated server never loads
 * this class.
 */
public final class AmbientContextClient {

    /** Ticks between periodic re-checks (~10 s at 20 tps). */
    public static final int PERIODIC_RECHECK_TICKS = 200;
    /** Minimum ticks between two dispatches, whatever triggered them. */
    public static final int MIN_DISPATCH_INTERVAL_TICKS = 20;

    private static final AmbientContextStore STORE = new AmbientContextStore();

    private static ClientLevel lastLevel;
    private static long lastDispatchTick = Long.MIN_VALUE;
    private static long nextPeriodicTick = Long.MIN_VALUE;
    private static boolean dispatchRequested;
    private static boolean equipmentKnown;
    private static long equipmentSignature;
    private static Integer environmentSignature;
    private static List<Long> lastShownKeys = List.of();

    private static long dispatches;
    private static long originsSent;
    private static long responsePackets;
    private static long requestBytes, responseBytes;
    private static long ticks;
    private static double totalTickMillis, peakTickMillis;
    private static String lastResponseSummary = "none";

    private AmbientContextClient() {
    }

    /**
     * One client tick. Does nothing unless the settings or development harness arm the runtime.
     */
    public static void tick(Minecraft mc, AmbientFishRuntime runtime) {
        if (!runtime.armed()) return;
        long started = System.nanoTime();
        try {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (level != lastLevel) {
            startSession(level);
        }
        if (mc.isPaused() || !level.tickRateManager().runsNormally()) return;
        if (mc.player == null) return;
        long tick = level.getGameTime();

        for (long patchKey : STORE.age(tick)) {
            // "No response" is supported: this is one debug line per aged patch, never an error,
            // and the periodic re-check will ask again.
            Fishtastic.LOGGER.debug("Ambient context request for patch {} aged out after {} ticks",
                    patchKey, AmbientContextStore.PENDING_TIMEOUT_TICKS);
        }

        updateEquipment(mc.player);

        List<AmbientFishRuntime.PatchSample> samples = runtime.patchSamples();
        // Watch observable environmental categories and cast-session changes promptly. This is
        // cheap input classification, never candidate resolution or a reward roll on the client.
        int environment = 1;
        for (var sample : samples) {
            var context = FishingPoolResolver.resolveEnvironment(level, sample.pos(), null);
            environment = 31 * environment + Objects.hash(context.timeOfDay(), context.weather(), context.moonPhase());
        }
        if (environmentSignature != null && environmentSignature != environment) {
            STORE.invalidateContexts();
            dispatchRequested = true;
        }
        environmentSignature = environment;
        List<Long> keys = new ArrayList<>(samples.size());
        boolean geometryMoved = false;
        for (AmbientFishRuntime.PatchSample sample : samples) {
            int before = STORE.geometryGenerationOf(sample.patchKey());
            int after = STORE.noteGeometry(sample.patchKey(), sample.geometrySignature());
            // A shown patch whose water geometry moved is a new question, and its applied answer
            // was computed against water that no longer exists. Comparing every tick is what keeps
            // the store from serving the stale sample cell until the next dispatch; a brand-new
            // patch (before == -1) is not a move — the key-set change above already asks for it.
            if (before != -1 && after != before) {
                geometryMoved = true;
            }
            keys.add(sample.patchKey());
        }
        if (!keys.equals(lastShownKeys) || geometryMoved) {
            lastShownKeys = keys;
            dispatchRequested = true;
        }

        if (nextPeriodicTick == Long.MIN_VALUE || tick >= nextPeriodicTick) {
            nextPeriodicTick = tick + PERIODIC_RECHECK_TICKS;
            dispatchRequested = true;
        }

        if (!dispatchRequested) return;
        if (lastDispatchTick != Long.MIN_VALUE && tick - lastDispatchTick < MIN_DISPATCH_INTERVAL_TICKS) {
            return;
        }
        dispatch(mc, level, samples, tick);
        } finally {
            double millis = (System.nanoTime() - started) / 1_000_000.0;
            ticks++;
            totalTickMillis += millis;
            peakTickMillis = Math.max(peakTickMillis, millis);
        }
    }

    /** A new level session: mint a session token, reset the store, ask again promptly. */
    private static void startSession(ClientLevel level) {
        lastLevel = level;
        long session;
        do {
            session = ThreadLocalRandom.current().nextLong();
        } while (session == 0L);
        STORE.reset(session);
        equipmentKnown = false;
        environmentSignature = null;
        nextPeriodicTick = Long.MIN_VALUE;
        lastDispatchTick = Long.MIN_VALUE;
        dispatchRequested = true;
        lastShownKeys = List.of();
    }

    /**
     * Resets the session state (reconnect, disconnect, reload). Called from the ambient facade's
     * lifecycle hooks, which run before any tick of the new session.
     */
    public static void resetSession() {
        lastLevel = null;
        long session;
        do {
            session = ThreadLocalRandom.current().nextLong();
        } while (session == 0L);
        STORE.reset(session);
        equipmentKnown = false;
        environmentSignature = null;
        nextPeriodicTick = Long.MIN_VALUE;
        lastDispatchTick = Long.MIN_VALUE;
        dispatchRequested = false;
        lastShownKeys = List.of();
        lastResponseSummary = "none";
    }

    /**
     * The species-affecting equipment inputs, hashed. This is exactly the {@link
     * FishingPoolResolver.Inputs} the server will resolve with — bait, charm and carried diamonds
     * through the shared helpers — so a change the fingerprint would notice is a change this
     * notices.
     */
    private static long equipmentSignature(Player player) {
        ItemStack rod = FishingMinigameManager.findFishtasticRod(player);
        FishingPoolResolver.Inputs inputs = rod.isEmpty()
                ? FishingPoolResolver.Inputs.standardWormsBaseline()
                : FishingPoolResolver.Inputs.heldRodPreview(rod, player);
        return Objects.hash(inputs.baitEffect(), inputs.charmEffect(),
                FishingPoolResolver.PersonalEquipment.fromInventory(player).inventoryDiamonds(),
                grill24.fishtastic.client.FishingMinigameClientHandler.getCurrentSessionId(),
                player.fishing == null ? null : player.fishing.blockPosition());
    }

    private static void updateEquipment(Player player) {
        long signature = equipmentSignature(player);
        if (!equipmentKnown) {
            equipmentKnown = true;
            equipmentSignature = signature;
            return;
        }
        if (signature != equipmentSignature) {
            equipmentSignature = signature;
            // Every applied answer was computed under the old inputs: the bump makes them stale,
            // and the very next dispatch asks for answers under the new ones. A response still in
            // flight from before this tick carries the old context generation and is discarded.
            STORE.invalidateContexts();
            dispatchRequested = true;
            Fishtastic.LOGGER.debug("Ambient context equipment changed; re-requesting at context generation {}",
                    STORE.contextGeneration());
        }
    }

    private static void dispatch(Minecraft mc, ClientLevel level,
                                 List<AmbientFishRuntime.PatchSample> samples, long tick) {
        dispatchRequested = false;
        lastDispatchTick = tick;
        if (mc.player == null) return;
        if (samples.isEmpty()) {
            STORE.retainPatches(List.of());
            return;
        }
        // The runtime shows at most MAX_DOMAINS == MAX_ORIGINS patches, so this can never cut a
        // real request; it exists so a future change to either constant can only shrink the
        // request, never throw an EncoderException from connection.send on the tick thread.
        if (samples.size() > AmbientContextRequestPacket.MAX_ORIGINS) {
            samples = samples.subList(0, AmbientContextRequestPacket.MAX_ORIGINS);
        }
        int requestGeneration = STORE.beginDispatch();
        Identifier dimension = level.dimension().identifier();
        List<AmbientContextRequestPacket.Origin> origins = new ArrayList<>(samples.size());
        List<Long> keys = new ArrayList<>(samples.size());
        for (AmbientFishRuntime.PatchSample sample : samples) {
            // The tick that requested this dispatch already noted the signature; this call only
            // stamps the resulting generation into the origin.
            int geometryGeneration = STORE.noteGeometry(sample.patchKey(), sample.geometrySignature());
            STORE.markPending(sample.patchKey(), requestGeneration, tick);
            keys.add(sample.patchKey());
            origins.add(new AmbientContextRequestPacket.Origin(dimension, sample.patchKey(),
                    geometryGeneration, STORE.contextGeneration(), sample.pos()));
        }
        STORE.retainPatches(keys);
        var packet = new AmbientContextRequestPacket(STORE.session(), requestGeneration, origins);
        requestBytes += encodedBytes(AmbientContextRequestPacket.STREAM_CODEC, packet, level);
        mc.player.connection.send(new ServerboundCustomPayloadPacket(packet));
        dispatches++;
        originsSent += origins.size();
        Fishtastic.LOGGER.debug("Ambient context request {} sent for {} origins (session={}, ctxGen={})",
                requestGeneration, origins.size(), STORE.session(), STORE.contextGeneration());
    }

    /** The registered client handler for {@link AmbientContextResponsePacket}. */
    public static void handle(AmbientContextResponsePacket packet) {
        responsePackets++;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) responseBytes += encodedBytes(AmbientContextResponsePacket.STREAM_CODEC, packet, level);
        List<AmbientContextStore.EntryOutcome> outcomes = STORE.accept(packet);
        int applied = 0;
        StringBuilder summary = new StringBuilder(64);
        for (AmbientContextStore.EntryOutcome outcome : outcomes) {
            if (outcome.outcome() == AmbientContextStore.Outcome.APPLIED_OK
                    || outcome.outcome() == AmbientContextStore.Outcome.APPLIED_REJECTED) {
                applied++;
            }
            summary.append(' ').append(outcome.patchKey()).append(':').append(outcome.outcome());
        }
        lastResponseSummary = "resp#" + responsePackets + " reqGen=" + packet.requestGeneration()
                + " applied=" + applied + summary;
        if (applied > 0) {
            Fishtastic.LOGGER.debug("Ambient context response {}: {} of {} entries applied",
                    packet.requestGeneration(), applied, packet.entries().size());
        } else {
            Fishtastic.LOGGER.debug("Ambient context response {}: all {} entries discarded (session/tokens/order)",
                    packet.requestGeneration(), packet.entries().size());
        }
    }

    /** Test/harness seam: applies a response through the same path the packet handler uses. */
    public static void injectForTest(AmbientContextResponsePacket packet) {
        handle(packet);
    }

    // ------------------------------------------------------------------ read seams

    public static AmbientContextStore store() {
        return STORE;
    }

    public static long dispatches() {
        return dispatches;
    }

    public static long originsSent() {
        return originsSent;
    }

    public static long responsePackets() {
        return responsePackets;
    }

    /** Ticks measured so far, and their total cost: for a windowed mean across a harness phase. */
    public static long ticks() {
        return ticks;
    }

    public static double totalTickMillis() {
        return totalTickMillis;
    }

    public static String lastResponseSummary() {
        return lastResponseSummary;
    }

    private static <T> int encodedBytes(net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> codec,
                                       T packet, ClientLevel level) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            codec.encode(buffer, packet);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    /** One line of internal state for the harness log. */
    public static String debugState() {
        StringBuilder sb = new StringBuilder(192);
        Map<Long, AmbientContextStore.State> states = STORE.states();
        sb.append("ctx session=").append(Long.toHexString(STORE.session()))
                .append(" ctxGen=").append(STORE.contextGeneration())
                .append(" reqGen=").append(STORE.requestGeneration())
                .append(" dispatches=").append(dispatches)
                .append(" origins=").append(originsSent)
                .append(" responses=").append(responsePackets)
                .append(" tickMs[mean=").append(totalTickMillis / Math.max(1, ticks))
                .append(" peak=").append(peakTickMillis).append(']')
                .append(" payloadBytes[request=").append(requestBytes).append(" response=").append(responseBytes).append(']')
                .append(" applied=").append(STORE.entriesApplied())
                .append(" timeouts=").append(STORE.timeouts())
                .append(" dropped[session=").append(STORE.droppedOldSession())
                .append(" geom=").append(STORE.droppedStaleGeometry())
                .append(" ctx=").append(STORE.droppedStaleContext())
                .append(" older=").append(STORE.droppedOlderRequest())
                .append(" unknown=").append(STORE.droppedUnknownPatch())
                .append(']');
        for (Map.Entry<Long, AmbientContextStore.State> entry : states.entrySet()) {
            sb.append(" [key=").append(entry.getKey()).append(' ').append(entry.getValue());
            AmbientContextStore.Context context = STORE.freshContext(entry.getKey());
            if (context != null) {
                sb.append(" fp=").append(Long.toHexString(context.fingerprint()))
                        .append(" n=").append(context.candidates().size())
                        .append(" mode=").append(context.sessionSourced() ? "session" : context.heldRod() ? "rod" : "worms");
            } else if (entry.getValue() == AmbientContextStore.State.REJECTED) {
                sb.append(" ").append(STORE.rejectionOf(entry.getKey()));
            }
            sb.append(']');
        }
        sb.append(" last=").append(lastResponseSummary);
        return sb.toString();
    }
}
