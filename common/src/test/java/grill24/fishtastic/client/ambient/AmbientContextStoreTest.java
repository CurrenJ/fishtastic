package grill24.fishtastic.client.ambient;

import grill24.fishtastic.network.AmbientContextResponsePacket;
import grill24.fishtastic.network.AmbientContextResponsePacket.Candidate;
import grill24.fishtastic.network.AmbientContextResponsePacket.Entry;
import grill24.fishtastic.network.AmbientContextResponsePacket.Status;
import grill24.fishtastic.util.Ids;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the client-side versioning/discard state machine of the ambient context store (Fish in
 * World, Phase 4). All headless: the store's whole contract is session tokens, geometry/context
 * generations and request ordering, and none of it needs a running world.
 */
class AmbientContextStoreTest {

    private static final long SESSION = 0x5E5510AB_C01L;
    private static final long PATCH = 77L;

    private static Candidate cod() {
        return new Candidate(Ids.of("minecraft", "cod"), 10);
    }

    private static Entry ok(AmbientContextStore store, long fingerprint, Candidate... candidates) {
        return ok(store, PATCH, fingerprint, candidates);
    }

    private static Entry ok(AmbientContextStore store, long patchKey, long fingerprint, Candidate... candidates) {
        return entry(patchKey, 1, store.contextGeneration(), Status.OK, fingerprint, candidates);
    }

    private static Entry entry(long patchKey, int geometry, int contextGeneration, Status status,
                               long fingerprint, Candidate... candidates) {
        return new Entry(patchKey, geometry, contextGeneration, status, false, false, false,
                fingerprint, List.of(candidates));
    }

    /** A store with one patch whose geometry generation is 1. */
    private static AmbientContextStore storeWithPatch() {
        AmbientContextStore store = new AmbientContextStore(SESSION);
        store.noteGeometry(PATCH, 100L);
        return store;
    }

    private static AmbientContextResponsePacket response(AmbientContextStore store, int requestGeneration,
                                                         Entry... entries) {
        return new AmbientContextResponsePacket(store.session(), requestGeneration, List.of(entries));
    }

    @Test
    void appliedAnswerIsUsableAndCarriesItsCandidates() {
        AmbientContextStore store = storeWithPatch();
        int request = store.beginDispatch();
        store.markPending(PATCH, request, 0L);

        List<AmbientContextStore.EntryOutcome> outcomes = store.accept(response(store, request, ok(store, 0xABCDL, cod())));

        assertEquals(List.of(AmbientContextStore.Outcome.APPLIED_OK), outcomes.stream().map(AmbientContextStore.EntryOutcome::outcome).toList());
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH));
        AmbientContextStore.Context context = store.freshContext(PATCH);
        assertNotNull(context);
        assertEquals(0xABCDL, context.fingerprint());
        assertEquals(1, context.candidates().size());
        assertEquals(1, store.entriesApplied());
    }

    @Test
    void responseToAnOlderRequestNeverOverwritesANewerOne() {
        AmbientContextStore store = storeWithPatch();
        int first = store.beginDispatch();
        store.markPending(PATCH, first, 0L);
        store.accept(response(store, first, ok(store, 0x1111L, cod())));
        int second = store.beginDispatch();
        store.markPending(PATCH, second, 0L);
        store.accept(response(store, second, ok(store, 0x2222L, cod())));

        // A late response to the first request arrives after the second was applied.
        List<AmbientContextStore.EntryOutcome> outcomes = store.accept(response(store, first, ok(store, 0x9999L, cod())));

        assertEquals(AmbientContextStore.Outcome.DROPPED_OLDER_REQUEST, outcomes.get(0).outcome());
        assertEquals(0x2222L, store.freshContext(PATCH).fingerprint());
        assertEquals(1, store.droppedOlderRequest());
    }

    /**
     * The ordering check is "strictly newer", not "not older": a response re-using the generation
     * that was already applied is dropped too. (Two responses can only share a generation if one is
     * a re-send or the server duplicated an answer; either way the applied value must win.)
     */
    @Test
    void equalGenerationResponseIsDroppedByRequestOrdering() {
        AmbientContextStore store = storeWithPatch();
        int request = store.beginDispatch();
        store.markPending(PATCH, request, 0L);
        store.accept(response(store, request, ok(store, 0x1111L, cod())));
        assertEquals(0x1111L, store.freshContext(PATCH).fingerprint());

        // The very same request generation arrives again (a re-send), with a different fingerprint.
        List<AmbientContextStore.EntryOutcome> outcomes =
                store.accept(response(store, request, ok(store, 0x9999L, cod())));

        assertEquals(AmbientContextStore.Outcome.DROPPED_OLDER_REQUEST, outcomes.get(0).outcome());
        assertEquals(0x1111L, store.freshContext(PATCH).fingerprint(),
                "an equal request generation is not newer and must not overwrite the applied value");
        assertEquals(1, store.droppedOlderRequest());
    }

    /**
     * A rejection is subject to the same ordering as an OK answer: one that only answers an older
     * request cannot unseat the applied value or turn the patch into REJECTED.
     */
    @Test
    void rejectionAnsweringAnOlderRequestCannotUnseatTheAppliedValue() {
        AmbientContextStore store = storeWithPatch();
        int first = store.beginDispatch();
        store.markPending(PATCH, first, 0L);
        store.accept(response(store, first, ok(store, 0x1111L, cod())));
        int second = store.beginDispatch();
        store.markPending(PATCH, second, 0L);
        store.accept(response(store, second, ok(store, 0x2222L, cod())));
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH));

        // A rejection for the *first* request arrives late: dropped by ordering, like any answer.
        List<AmbientContextStore.EntryOutcome> late = store.accept(response(store, first,
                entry(PATCH, 1, store.contextGeneration(), Status.REJECTED_NOT_WATER, 0L)));

        assertEquals(AmbientContextStore.Outcome.DROPPED_OLDER_REQUEST, late.get(0).outcome());
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH));
        assertEquals(0x2222L, store.freshContext(PATCH).fingerprint());
        assertNull(store.rejectionOf(PATCH));
        assertEquals(0, store.rejectionsApplied());
    }

    @Test
    void equipmentChangeDiscardsInFlightResponseAndStalesTheValue() {
        AmbientContextStore store = storeWithPatch();
        int before = store.beginDispatch();
        store.markPending(PATCH, before, 0L);
        store.accept(response(store, before, ok(store, 0x1111L, cod())));
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH));

        // The player switches to a rod: a new context generation, and a new dispatch.
        int generationBeforeSwitch = store.contextGeneration();
        store.invalidateContexts();
        assertEquals(AmbientContextStore.State.STALE, store.stateOf(PATCH));
        assertNull(store.freshContext(PATCH));
        int after = store.beginDispatch();
        store.markPending(PATCH, after, 0L);

        // The answer to the pre-switch request arrives late: it carries the generation it was
        // dispatched under (the pre-switch one), so it must not apply.
        List<AmbientContextStore.EntryOutcome> late = store.accept(response(store, before,
                entry(PATCH, 1, generationBeforeSwitch, Status.OK, 0x9999L, cod())));
        assertEquals(AmbientContextStore.Outcome.DROPPED_STALE_CONTEXT, late.get(0).outcome());
        assertNull(store.freshContext(PATCH));

        // The answer to the post-switch request does apply, with the new fingerprint.
        store.accept(response(store, after, ok(store, 0x3333L, cod())));
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH));
        assertEquals(0x3333L, store.freshContext(PATCH).fingerprint());
        assertEquals(1, store.droppedStaleContext());
    }

    @Test
    void geometryChangeDiscardsAnswersForTheOldWater() {
        AmbientContextStore store = storeWithPatch();
        int request = store.beginDispatch();
        store.markPending(PATCH, request, 0L);

        // The patch's water changed under the request: the signature moves the generation on.
        store.noteGeometry(PATCH, 200L);

        List<AmbientContextStore.EntryOutcome> outcomes = store.accept(response(store, request, ok(store, 0x1111L, cod())));
        assertEquals(AmbientContextStore.Outcome.DROPPED_STALE_GEOMETRY, outcomes.get(0).outcome());
        assertNull(store.freshContext(PATCH));
        assertEquals(1, store.droppedStaleGeometry());
    }

    @Test
    void responsesFromAPreviousSessionAreDiscarded() {
        AmbientContextStore store = storeWithPatch();
        store.accept(new AmbientContextResponsePacket(SESSION + 1, 1,
                List.of(ok(store, 0x1111L, cod()))));

        assertEquals(0, store.entriesApplied());
        assertEquals(AmbientContextStore.State.NONE, store.stateOf(PATCH));
        assertEquals(1, store.droppedOldSession());
    }

    @Test
    void responsesForUnknownPatchesAreDiscarded() {
        AmbientContextStore store = storeWithPatch();
        List<AmbientContextStore.EntryOutcome> outcomes =
                store.accept(response(store, 1, ok(store, PATCH + 1, 0x1111L, cod())));

        assertEquals(AmbientContextStore.Outcome.DROPPED_UNKNOWN_PATCH, outcomes.get(0).outcome());
        assertEquals(1, store.droppedUnknownPatch());
    }

    @Test
    void rejectionMakesThePatchUnusableUntilTokensMoveOn() {
        AmbientContextStore store = storeWithPatch();
        int request = store.beginDispatch();
        store.markPending(PATCH, request, 0L);
        store.accept(response(store, request, ok(store, 0x1111L, cod())));
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH));

        int next = store.beginDispatch();
        store.markPending(PATCH, next, 100L);
        store.accept(response(store, next, entry(PATCH, 1, store.contextGeneration(), Status.REJECTED_NOT_WATER, 0L)));

        assertEquals(AmbientContextStore.State.REJECTED, store.stateOf(PATCH));
        assertNull(store.freshContext(PATCH));
        assertEquals(Status.REJECTED_NOT_WATER, store.rejectionOf(PATCH));
        assertEquals(1, store.rejectionsApplied());

        // A rejection belongs to the tokens it answered: moving the geometry on clears it.
        store.noteGeometry(PATCH, 300L);
        assertEquals(AmbientContextStore.State.NONE, store.stateOf(PATCH));
        assertNull(store.rejectionOf(PATCH));
    }

    @Test
    void unansweredPendingPatchAgesOutWithoutLosingAPreviousValue() {
        AmbientContextStore store = storeWithPatch();
        int first = store.beginDispatch();
        store.markPending(PATCH, first, 0L);
        store.accept(response(store, first, ok(store, 0x1111L, cod())));

        // A periodic refresh is dispatched and the server never answers it.
        int refresh = store.beginDispatch();
        store.markPending(PATCH, refresh, 10L);
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH), "a refresh keeps the last value usable");
        assertTrue(store.hasPending());

        assertTrue(store.age(10L + AmbientContextStore.PENDING_TIMEOUT_TICKS).isEmpty());
        List<Long> agedOut = store.age(10L + AmbientContextStore.PENDING_TIMEOUT_TICKS + 1);

        assertEquals(List.of(PATCH), agedOut);
        assertEquals(1, store.timeouts());
        assertTrue(!store.hasPending());
        assertEquals(AmbientContextStore.State.OK, store.stateOf(PATCH));
    }

    @Test
    void unansweredFirstRequestAgesOutIntoSpawnNothing() {
        AmbientContextStore store = storeWithPatch();
        int request = store.beginDispatch();
        store.markPending(PATCH, request, 0L);
        assertEquals(AmbientContextStore.State.PENDING, store.stateOf(PATCH));

        store.age(AmbientContextStore.PENDING_TIMEOUT_TICKS + 1);

        assertEquals(AmbientContextStore.State.NONE, store.stateOf(PATCH));
        assertNull(store.freshContext(PATCH));
    }

    @Test
    void retainKeepsOnlyShownPatchesAndTheBoundHolds() {
        AmbientContextStore store = new AmbientContextStore(SESSION);
        for (long key = 1; key <= 5; key++) {
            store.noteGeometry(key, key);
        }
        assertEquals(5, store.trackedPatches());

        store.retainPatches(List.of(2L, 4L));

        assertEquals(2, store.trackedPatches());
        assertEquals(AmbientContextStore.State.NONE, store.stateOf(1L));
        assertEquals(AmbientContextStore.State.NONE, store.stateOf(4L));
        assertTrue(store.states().containsKey(4L));
    }

    @Test
    void resetMintsANewLifeAndBumpsTheContextGeneration() {
        AmbientContextStore store = storeWithPatch();
        int request = store.beginDispatch();
        store.markPending(PATCH, request, 0L);
        store.accept(response(store, request, ok(store, 0x1111L, cod())));
        int contextGenerationBefore = store.contextGeneration();

        store.reset(SESSION + 5);

        assertEquals(SESSION + 5, store.session());
        assertEquals(contextGenerationBefore + 1, store.contextGeneration());
        assertEquals(0, store.requestGeneration());
        assertEquals(0, store.trackedPatches());
        assertEquals(AmbientContextStore.State.NONE, store.stateOf(PATCH));
    }
}
