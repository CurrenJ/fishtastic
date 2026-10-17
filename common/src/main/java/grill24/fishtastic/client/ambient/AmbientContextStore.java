package grill24.fishtastic.client.ambient;

import grill24.fishtastic.network.AmbientContextResponsePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side state machine for authoritative ambient species contexts (Fish in World, Phase 4).
 *
 * <p>Pure Java — no Minecraft types beyond the packet records — so the versioning rules are pinned
 * by plain JUnit tests rather than only by a running world.
 *
 * <h2>Tokens and what they protect</h2>
 * Every response entry must satisfy three checks before it may replace anything:
 * <ol>
 *   <li><b>Session</b> — the response must belong to the store's current connection session. A
 *       response from before a level change or reconnect can never apply.</li>
 *   <li><b>Token match</b> — the entry's {@code geometryGeneration} and {@code contextGeneration}
 *       must equal the store's current values. A geometry bump means the water changed under the
 *       answer; a context bump means equipment (or another context input) changed, so any answer
 *       already in flight was computed from the old inputs. Together these are what make an
 *       equipment change impossible to overwrite with a response to an older request.</li>
 *   <li><b>Ordering</b> — the response's {@code requestGeneration} must be strictly newer than the
 *       last one applied for that patch. Late-arriving answers lose.</li>
 * </ol>
 *
 * <h2>States</h2>
 * Each patch's state is <em>derived</em>, never mutated by invalidation: the applied answer records
 * the generations it was computed under, so a bump automatically makes it stale without touching
 * values. Consumers (Phase 5's population sampler) may use a patch only in {@link State#OK};
 * everything else — including {@link State#REJECTED}, a server saying "that is not water any more"
 * — must be treated as "no known pool".
 *
 * <p>Pending requests age out after {@link #PENDING_TIMEOUT_TICKS} without an error and without
 * losing a previously-known stale value; "no response" is a supported state.
 */
public final class AmbientContextStore {

    /** How long a dispatched request may stay unanswered before the patch's pending state ages out. */
    public static final int PENDING_TIMEOUT_TICKS = 100;
    /** Hard bound on tracked patches; the shown set is far smaller. */
    public static final int MAX_PATCHES = 16;

    /** What a caller may believe about a patch right now. */
    public enum State {
        /** No answer has ever been applied; do not spawn from this patch. */
        NONE,
        /** A request is in flight (or aged out) with no current value; do not spawn. */
        PENDING,
        /** A current authoritative answer is applied; this is the usable state. */
        OK,
        /** An answer existed but its tokens/generations moved on; do not spawn until re-answered. */
        STALE,
        /** The server refused the current tokens (not water / unloaded); do not spawn. */
        REJECTED
    }

    /** A usable authoritative answer, as the packet carried it. */
    public record Context(boolean sessionSourced, boolean heldRod, boolean truncated, long fingerprint,
                          List<AmbientContextResponsePacket.Candidate> candidates) {
        public Context {
            candidates = List.copyOf(candidates);
        }
    }

    /** Why one response entry was or was not applied — the unit tests' and logs' window. */
    public enum Outcome {
        APPLIED_OK, APPLIED_REJECTED,
        DROPPED_OLD_SESSION, DROPPED_UNKNOWN_PATCH, DROPPED_STALE_GEOMETRY, DROPPED_STALE_CONTEXT,
        DROPPED_OLDER_REQUEST
    }

    public record EntryOutcome(long patchKey, Outcome outcome) {
    }

    private static final class Patch {
        int geometryGeneration = 1;
        long geometrySignature = Long.MIN_VALUE;
        int pendingRequestGeneration = -1;
        long pendingSinceTick = Long.MIN_VALUE;
        int appliedRequestGeneration = -1;
        boolean hasValue;
        boolean valueSessionSourced;
        boolean valueHeldRod;
        boolean valueTruncated;
        long valueFingerprint;
        List<AmbientContextResponsePacket.Candidate> valueCandidates = List.of();
        int valueContextGeneration;
        int valueGeometryGeneration;
        AmbientContextResponsePacket.Status rejection;
        int rejectionContextGeneration;
        int rejectionGeometryGeneration;
    }

    private final Map<Long, Patch> patches = new HashMap<>();

    private long session;
    private int contextGeneration;
    private int requestGeneration;

    // Counters, cumulative since the last reset — diagnostics for the harness and the tests.
    private long responsesIn;
    private long entriesApplied;
    private long rejectionsApplied;
    private long droppedOldSession;
    private long droppedUnknownPatch;
    private long droppedStaleGeometry;
    private long droppedStaleContext;
    private long droppedOlderRequest;
    private long timeouts;

    public AmbientContextStore() {
        this(0L);
    }

    public AmbientContextStore(long session) {
        this.session = session;
    }

    // ------------------------------------------------------------------ tokens

    public long session() {
        return session;
    }

    public int contextGeneration() {
        return contextGeneration;
    }

    public int requestGeneration() {
        return requestGeneration;
    }

    /** Starts a new store life: connection/level session change. Bumps the context generation. */
    public void reset(long newSession) {
        session = newSession;
        patches.clear();
        contextGeneration++;
        requestGeneration = 0;
    }

    /**
     * Context inputs other than a patch's geometry changed (equipment lane, explicit invalidation).
     * Applied answers stop being fresh automatically: they record the generation they were built
     * under, and this increment moves past it.
     */
    public void invalidateContexts() {
        contextGeneration++;
    }

    /** The generation a fresh dispatch's origins should stamp. */
    public int beginDispatch() {
        return ++requestGeneration;
    }

    // ------------------------------------------------------------------ geometry

    /**
     * Samples a patch's geometry (an opaque signature the manager computes) and returns the
     * generation to stamp on requests. The first sample creates the patch; a changed signature
     * bumps the generation, which is what later discards answers for the old geometry.
     */
    public int noteGeometry(long patchKey, long signature) {
        Patch patch = patches.get(patchKey);
        if (patch == null) {
            patch = new Patch();
            patch.geometrySignature = signature;
            putBounded(patchKey, patch);
            return patch.geometryGeneration;
        }
        if (patch.geometrySignature != signature) {
            patch.geometrySignature = signature;
            patch.geometryGeneration++;
        }
        return patch.geometryGeneration;
    }

    /** Forgets patches other than these (the currently shown set). Bounded: the map never grows. */
    public void retainPatches(Iterable<Long> shownKeys) {
        List<Long> keep = new ArrayList<>();
        for (long key : shownKeys) keep.add(key);
        patches.keySet().removeIf(key -> !keep.contains(key));
    }

    private void putBounded(long patchKey, Patch patch) {
        if (patches.size() >= MAX_PATCHES) {
            // The manager retains to the shown set every dispatch, so this only trips for a caller
            // that never retains; dropping one arbitrary entry is the bound, not an error.
            var it = patches.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        patches.put(patchKey, patch);
    }

    // ------------------------------------------------------------------ pending

    /** Records that a request containing this patch was just dispatched. */
    public void markPending(long patchKey, int requestGeneration, long tick) {
        Patch patch = patches.get(patchKey);
        if (patch == null) return;
        patch.pendingRequestGeneration = requestGeneration;
        patch.pendingSinceTick = tick;
    }

    /**
     * Ages pending patches: one that has gone {@link #PENDING_TIMEOUT_TICKS} without any answer
     * loses its pending mark (keeping any previously applied, now-stale value).
     *
     * @return the patch keys that timed out this call, for one debug log each
     */
    public List<Long> age(long nowTick) {
        List<Long> timedOut = null;
        for (Map.Entry<Long, Patch> entry : patches.entrySet()) {
            Patch patch = entry.getValue();
            if (patch.pendingRequestGeneration < 0 || patch.pendingSinceTick == Long.MIN_VALUE) continue;
            if (nowTick - patch.pendingSinceTick > PENDING_TIMEOUT_TICKS) {
                patch.pendingRequestGeneration = -1;
                patch.pendingSinceTick = Long.MIN_VALUE;
                timeouts++;
                if (timedOut == null) timedOut = new ArrayList<>();
                timedOut.add(entry.getKey());
            }
        }
        return timedOut == null ? List.of() : timedOut;
    }

    /** Whether any patch currently has an unanswered dispatch. */
    public boolean hasPending() {
        for (Patch patch : patches.values()) {
            if (patch.pendingRequestGeneration >= 0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ responses

    /**
     * Applies one response: every entry is checked against the session, the patch's current tokens
     * and the applied-request ordering. Returns one outcome per entry, in packet order.
     */
    public List<EntryOutcome> accept(AmbientContextResponsePacket packet) {
        responsesIn++;
        List<EntryOutcome> outcomes = new ArrayList<>(packet.entries().size());
        boolean oldSession = packet.session() != session;
        for (AmbientContextResponsePacket.Entry entry : packet.entries()) {
            if (oldSession) {
                droppedOldSession++;
                outcomes.add(new EntryOutcome(entry.patchKey(), Outcome.DROPPED_OLD_SESSION));
                continue;
            }
            Patch patch = patches.get(entry.patchKey());
            if (patch == null) {
                droppedUnknownPatch++;
                outcomes.add(new EntryOutcome(entry.patchKey(), Outcome.DROPPED_UNKNOWN_PATCH));
                continue;
            }
            if (entry.geometryGeneration() != patch.geometryGeneration) {
                droppedStaleGeometry++;
                outcomes.add(new EntryOutcome(entry.patchKey(), Outcome.DROPPED_STALE_GEOMETRY));
                continue;
            }
            if (entry.contextGeneration() != contextGeneration) {
                droppedStaleContext++;
                outcomes.add(new EntryOutcome(entry.patchKey(), Outcome.DROPPED_STALE_CONTEXT));
                continue;
            }
            if (packet.requestGeneration() <= patch.appliedRequestGeneration) {
                droppedOlderRequest++;
                outcomes.add(new EntryOutcome(entry.patchKey(), Outcome.DROPPED_OLDER_REQUEST));
                continue;
            }
            patch.appliedRequestGeneration = packet.requestGeneration();
            patch.pendingRequestGeneration = -1;
            patch.pendingSinceTick = Long.MIN_VALUE;
            if (entry.status() == AmbientContextResponsePacket.Status.OK) {
                patch.hasValue = true;
                patch.valueSessionSourced = entry.sessionSourced();
                patch.valueHeldRod = entry.heldRod();
                patch.valueTruncated = entry.truncated();
                patch.valueFingerprint = entry.fingerprint();
                patch.valueCandidates = List.copyOf(entry.candidates());
                patch.valueContextGeneration = entry.contextGeneration();
                patch.valueGeometryGeneration = entry.geometryGeneration();
                patch.rejection = null;
                entriesApplied++;
                outcomes.add(new EntryOutcome(entry.patchKey(), Outcome.APPLIED_OK));
            } else {
                // A rejection is "no known pool" for the current tokens: the stale value, if any,
                // must not stay usable — the server just said the origin is no longer water.
                patch.hasValue = false;
                patch.valueCandidates = List.of();
                patch.rejection = entry.status();
                patch.rejectionContextGeneration = entry.contextGeneration();
                patch.rejectionGeometryGeneration = entry.geometryGeneration();
                rejectionsApplied++;
                outcomes.add(new EntryOutcome(entry.patchKey(), Outcome.APPLIED_REJECTED));
            }
        }
        return List.copyOf(outcomes);
    }

    // ------------------------------------------------------------------ queries

    /** The derived state of one patch; unknown patches are {@link State#NONE}. */
    public State stateOf(long patchKey) {
        Patch patch = patches.get(patchKey);
        if (patch == null) return State.NONE;
        if (patch.hasValue
                && patch.valueContextGeneration == contextGeneration
                && patch.valueGeometryGeneration == patch.geometryGeneration) {
            return State.OK;
        }
        if (patch.rejection != null
                && patch.rejectionContextGeneration == contextGeneration
                && patch.rejectionGeometryGeneration == patch.geometryGeneration) {
            return State.REJECTED;
        }
        if (patch.pendingRequestGeneration >= 0) return State.PENDING;
        if (patch.hasValue) return State.STALE;
        return State.NONE;
    }

    /** The current authoritative answer, or null unless {@link #stateOf} says {@link State#OK}. */
    public Context freshContext(long patchKey) {
        if (stateOf(patchKey) != State.OK) return null;
        Patch patch = patches.get(patchKey);
        return new Context(patch.valueSessionSourced, patch.valueHeldRod, patch.valueTruncated,
                patch.valueFingerprint, patch.valueCandidates);
    }

    /**
     * The current geometry generation of a patch, or {@code -1} when the patch is unknown. The
     * harness's stale-response builder needs the exact token a request would carry; nothing else
     * reads it (a caller must never infer freshness from it — use {@link #stateOf}).
     */
    public int geometryGenerationOf(long patchKey) {
        Patch patch = patches.get(patchKey);
        return patch == null ? -1 : patch.geometryGeneration;
    }

    /** The rejection status currently in force for a patch, or null. */
    public AmbientContextResponsePacket.Status rejectionOf(long patchKey) {
        return stateOf(patchKey) == State.REJECTED && patches.get(patchKey) != null
                ? patches.get(patchKey).rejection
                : null;
    }

    public int trackedPatches() {
        return patches.size();
    }

    /** Every tracked patch's derived state, for diagnostics and the harness. */
    public Map<Long, State> states() {
        Map<Long, State> out = new java.util.LinkedHashMap<>();
        for (long key : patches.keySet()) {
            out.put(key, stateOf(key));
        }
        return out;
    }

    // Counters

    public long responsesIn() {
        return responsesIn;
    }

    public long entriesApplied() {
        return entriesApplied;
    }

    public long rejectionsApplied() {
        return rejectionsApplied;
    }

    public long droppedOldSession() {
        return droppedOldSession;
    }

    public long droppedUnknownPatch() {
        return droppedUnknownPatch;
    }

    public long droppedStaleGeometry() {
        return droppedStaleGeometry;
    }

    public long droppedStaleContext() {
        return droppedStaleContext;
    }

    public long droppedOlderRequest() {
        return droppedOlderRequest;
    }

    public long timeouts() {
        return timeouts;
    }
}
