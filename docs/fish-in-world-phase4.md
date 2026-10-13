# Fish in World - Phase 4 handoff (authoritative context snapshots)

Session continuation checkpoint: [fish-in-world-handoff.md](fish-in-world-handoff.md).
Plan: [fish-in-world-implementation-plan.md](fish-in-world-implementation-plan.md) §Phase 4.
Phase 3 (species-context resolver) is closed; see [fish-in-world-phase3.md](fish-in-world-phase3.md).
Status: **implemented, independently reviewed and fixed 2026-10-13** — an independent read-only
review verified all four gate clauses clean and raised two should-fixes plus nits; the fix round
landed every item, one discovery defect found while reproducing them, and one long-standing
regression the dedicated re-run exposed (eviction window shaped differently from the sweep — see
*Review round and fix round*). Coverage: plain JUnit tests, codec round-trip gametests, world
gametests, a solo client scene and a real dedicated-server session (Fabric; NeoForge covered by its
gametest run, see *Loader scope*).

**Nothing spawns from a snapshot yet.** Phase 4 is the pipeline, not the population: the fixed dev
pool display is unchanged, fishing behavior is untouched (the Phase 3 resolver is *consumed*, not
modified), and there are no density/settings knobs (Phases 5–6). A client sends no context traffic
at all unless the ambient runtime is armed (`ClientAmbientFish.startNatural()` behind
`DevEnvironmentCheck`); normal play behaves exactly as before.

## Protocol

Two payloads on the common packet abstraction (`FishtasticPackets`), registered by both loaders;
the client handler is registered from each loader's client entrypoint only.

| | `AmbientContextRequestPacket` (client → server) | `AmbientContextResponsePacket` (server → client) |
|---|---|---|
| Fields | `session` long, `requestGeneration` int, `origins` list (≤ `MAX_ORIGINS` 4) | `session` long, `requestGeneration` int, `entries` list (≤ `MAX_ENTRIES` 4) |
| Per-origin / per-entry | `Origin`: `dimension` Identifier, `patchKey` long, `geometryGeneration` int, `contextGeneration` int, `pos` BlockPos | `Entry`: `patchKey`, `geometryGeneration`, `contextGeneration`, `status` (OK / REJECTED_DIMENSION / REJECTED_FAR / REJECTED_UNLOADED / REJECTED_NOT_WATER), `sessionSourced` bool, `heldRod` bool, `truncated` bool, `fingerprint` long, `candidates` list (≤ `MAX_CANDIDATES` 96) |
| Candidate | — | `Candidate`: `species` Identifier, `weight` int (final raw weight, pool order) |
| Codec | `StreamCodec.composite` with VAR_LONG / VAR_INT / `Identifier.STREAM_CODEC` / `BlockPos.STREAM_CODEC` / idMapper status / BOOL / `ByteBufCodecs.list(4)` | same primitives, `ByteBufCodecs.list(4)` entries, each `ByteBufCodecs.list(96)` candidates |

Bounds are enforced by the codecs on decode: the client can only ever send the runtime's shown
patches (≤ `AmbientFishRuntime.MAX_DOMAINS`, which **is** `AmbientContextRequestPacket.MAX_ORIGINS`
by construction — one constant derives the other — plus a defensive clamp in the dispatch so a
future constant drift can only shrink a request, never throw from `connection.send` on the tick
thread), and the server caps each answered pool at 96 candidates (`truncated` marks the cap), so
neither side can encode past a wire bound by construction — a larger packet would decode-throw and
disconnect (pinned on both encode and decode by the `oversized_*` gametests). A status id off the
wire that no version issued decodes to `REJECTED_NOT_WATER`, never an error and never `OK` (pinned
by `unknownStatusIdDecodesToARejectionNotAnError`).

### Token scheme

Every request origin carries `(session, patchKey, geometryGeneration, contextGeneration)`, and the
envelope carries `requestGeneration`. A response entry can replace an applied value only if, in
this order:

1. **Session** — the response's `session` equals the store's current session (minted per
   level/connection; a response from before a reconnect/reload can never apply).
2. **Patch exists** — the patch is still tracked (shown patches only; `retainPatches` drops the
   rest each dispatch).
3. **Geometry** — `geometryGeneration` equals the patch's current value (the water changed under
   the answer, e.g. a pond filled in or discovered differently).
4. **Context** — `contextGeneration` equals the store's current value (equipment or another
   context input changed, so anything in flight was computed from the old inputs).
5. **Ordering** — `requestGeneration` strictly newer than the last one *applied* for that patch.

Checks 3–5 are also the versioned-discard story: a rod switch bumps the context generation, which
makes the applied answer stale and every in-flight response for the old inputs droppable, while an
answer to an older request can never overwrite a newer one.

## Server side (`AmbientContextServer`, `AmbientRequestThrottle`)

Validation order per origin — **nothing reads world state before this sequence**:

1. Dimension must equal the player's current dimension (`REJECTED_DIMENSION`).
2. Within `MAX_ORIGIN_HORIZONTAL_DISTANCE` / `MAX_ORIGIN_VERTICAL_DISTANCE` 64 / 64 blocks
   (`REJECTED_FAR`) — the client's discovery window is much smaller, so a legitimate client is
   never rejected and a forged request cannot sweep arbitrary loaded regions.
3. `level.isLoaded(pos)` — a `hasChunk` check; the server never loads or generates a chunk to
   answer (`REJECTED_UNLOADED`; the gametest asserts the chunk stays unloaded).
4. The origin cell must hold water *now* (`REJECTED_NOT_WATER`) — a stale client view is rejected,
   not silently resolved elsewhere.

Only then is anything resolved, on the main thread (the handler enqueues work). Resolution uses the
Phase 3 `FishingPoolResolver` with the **server's own** player state:
`Inputs.standardWormsBaseline()` with no supported rod, `Inputs.heldRodPreview(rod, player)` with
one — the same helpers the client's equipment signature uses, so the two cannot drift. An active
fishing session's `SessionSnapshot` takes precedence for origins within `SESSION_SNAPSHOT_RADIUS`
4 / `SESSION_SNAPSHOT_VERTICAL` 8 blocks of the cast origin — a **near-exact match, not a
discovery window** (review-round tightening: the old 32/24 Chebyshev radius answered a *different*
pond 6 blocks away from the cast as session-sourced). The snapshot's origin is the hook's cast
position and the client's origin for the same patch is one surface cell of the same water, so the
few blocks of slack cover the sample-cell-to-hook spread; an origin beyond it falls through to the
normal fresh resolve (still authoritative, just not the session's captured value). Patches exist
only in the client's discovery, so proximity to the cast is the closest server-side test to patch
identity; the gametest pins both the same-patch precedence and the neighbouring-pond fall-through.

**Rate limit:** one token bucket per player — `BURST_CAPACITY` 4, `REFILL_INTERVAL_TICKS` 20 (1/s),
dropped after `IDLE_EXPIRY_TICKS` 24000 idle. The tracked-player table is pruned **before every
insert**, so it never holds more than `MAX_TRACKED_PLAYERS` 512 buckets: a crowded table first
drops idle buckets, then evicts the least-recently-used one — never a wholesale clear, so an active
player's partially spent burst survives strangers arriving at the cap (pinned by
`AmbientContextServerRulesTest`). An empty-origins request is answered (with nothing) **before**
the throttle is touched: it costs no token and never disturbs the table. A throttled
request is answered with nothing at all — "no response" is a supported client state — and logs
`Ambient context request from {} dropped by rate limit` (none occurred in any capture). Responses
1–10 and every 100th are logged at INFO with `session`, `reqGen`, and per entry
`key / fp / n / mode=worms|rod|session` (the cross-process comparison evidence).

## Client side (`AmbientContextClient`, `AmbientContextStore`)

**Refresh policy — event-driven, plus one bounded periodic re-check.** At most one dispatch per
`MIN_DISPATCH_INTERVAL_TICKS` 20, containing only the runtime's shown patches, requested when:

- the shown patch set changed, or a shown patch's geometry signature moved — **both compared every
  tick** (the runtime samples signatures per tick anyway; each tick also `noteGeometry`s them into
  the store, so the store bumps the patch's geometry generation the moment discovery's cached read
  of the water moves, and a dispatch follows at once — subject to the 20-tick minimum — instead of
  waiting for the next dispatch to look). A world edit becomes visible to discovery when its
  budgeted rotation re-reads the edited column: `WaterDiscovery.tick` carries the revalidation pass
  itself (fix round — `revalidatePass` takes its allowance from the tick's residual cell budget
  above a reserve kept for the sweep, capped at `DEFAULT_REVALIDATE_COLUMNS_PER_TICK` 48 per tick
  on a rotating cursor, so a known column is re-read within a rotation or two even while dry land
  keeps the sweep busy; the runtime-side "whatever is left" arithmetic that used to drive this pass
  was always zero in exactly that regime and the pass never ran — RED reproduced by `fishsim`'s
  `theDriverItselfNoticesAnEditWhileDryLandKeepsTheSweepBusy`). No Phase 4 latency sits on top of
  that; the solo scene's `geometry.reask` phase measures exactly that two-stage timing — the move,
  then a bump and an applied answer within 30 ticks of it;
- the equipment lane changed — the species-affecting inputs of the held rod (bait, charm, carried
  diamonds) hashed through the same `FishingPoolResolver.Inputs` helpers the server resolves with;
  the store's context generation bumps and the next dispatch asks under the new one;
- the level/dimension changed or the session was reset (reconnect, reload);
- `PERIODIC_RECHECK_TICKS` 200 (~10 s) elapsed — the bounded check for server-side drift the client
  cannot see (time of day, weather, moon phase).

There is deliberately **no per-fish or per-frame resolution** anywhere: at steady state this is one
small request per 10 s, and the runtime's shown set is the only origin source.

**Discard state machine** (`AmbientContextStore`, pure Java — the rules are pinned by 13 JUnit
tests). Each patch's state is *derived*, never mutated by invalidation: an applied answer records
the generations it was computed under, so a bump makes it stale without touching values.

| State | Meaning for a consumer (Phase 5) |
|---|---|
| `NONE` / `PENDING` | No answer yet / one in flight (ages out after `PENDING_TIMEOUT_TICKS` 100) — spawn nothing |
| `OK` | Current authoritative answer — the only usable state (`freshContext`) |
| `STALE` | An answer existed but its tokens moved on — spawn nothing until re-answered |
| `REJECTED` | The server refused the current tokens (not water / unloaded) — the stale value is cleared with it |

"No response" is a supported state: a pending request ages out losing nothing worse than its
pending mark (a previously applied, now-stale value is kept), and the periodic re-check asks again.
Tracked patches are retained to the shown set every dispatch (hard bound `MAX_PATCHES` 16, far
above the shown count).

## Review round and fix round (2026-10-13)

An independent read-only review verified the gate clauses (protocol shape, token scheme, validation
order, bounds, server class loading) clean and raised two should-fixes plus nits. Every item was
fixed and RED-reproduced first where a reproduction was feasible (the two should-fixes, and the
coverage tests against the old behavior). While reproducing item 1, a second pre-existing defect
was found in the discovery driver (1b); the fix-round dedicated re-run then exposed one more (8).
No gate clause changed meaning through any fix.

| # | Finding (review unless noted) | Fix | Reproduction → green evidence |
|---|---|---|---|
| 1 | A geometry change that leaves the shown patch *keys* alone was invisible — the client compared only the key set, so an edit inside already-cached water never re-requested context. | The runtime folds every shown patch's columns (sorted x,z; `truncated`; span lo/hi bits — `AmbientFishRuntime.geometrySignature`) and the client compares that signature every tick; each tick also `noteGeometry`s it into the store, so the bump stales in-flight answers and a dispatch follows as soon as the 20-tick minimum allows. | RED `fixround/red-solo-fix1-console.log` (only `geometry.reask` FAILs — stage 2 shows no bump/dispatch/apply within 31 ticks; `dispatches=+0`) → GREEN `fixround/green-solo-console-fixed5.log` (`geomGen=11->12 state=OK applied=+1 stage2=1t dispatches=+1`; same result in run 4). |
| 1b | *(found while reproducing 1)* The discovery driver's revalidation pass was starved exactly where it matters: the runtime computed its allowance from the **post-tick** cell counter (`spare / band`), which is zero whenever dry land keeps the sweep busy — so a block broken inside cached water was never re-read on dry shores, and item 1's fix had nothing to see. | `WaterDiscovery.tick()` now carries the pass itself (`revalidatePass`): allowance `= max(0, spare − cellBudget/4) / band` taken between the near pass and the sweep, capped at `DEFAULT_REVALIDATE_COLUMNS_PER_TICK` 48 on a rotating cursor; the runtime-side arithmetic and its post-tick block are gone. | RED `fixround/red-fishsim-revalidate-starvation.log` → GREEN fishsim test `theDriverItselfNoticesAnEditWhileDryLandKeepsTheSweepBusy` (the drained column is noticed within the budget while the sweep keeps moving). |
| 2 | Session-snapshot precedence used a 32/24-block Chebyshev radius, so a **different** pond 6 blocks from the cast was answered with the cast's captured values. | Precedence is a near-exact match to the cast origin — `SESSION_SNAPSHOT_RADIUS` 4 / `SESSION_SNAPSHOT_VERTICAL` 8 — and anything beyond falls through to a fresh (still authoritative) resolve. | RED `fixround/red-fabric-gametest.log` → GREEN `fixround/green-fabric-gametest.log` gametests `sessionSnapshotTakesPrecedenceOverAFreshResolve` + `aDifferentPondNearTheCastFallsThroughToAFreshResolve`. |
| 3 | `AmbientFishRuntime.MAX_DOMAINS` and `AmbientContextRequestPacket.MAX_ORIGINS` are one constant by construction, but nothing stopped a future drift from encoding a request the client cannot decode. | Defensive clamp in dispatch: the origin list is capped at `MAX_ORIGINS` before `connection.send`, so drift can only shrink a request, never throw on the tick thread. | Belt-and-braces (no separate RED); the codec bound stays pinned by `oversizedOriginListIsRejectedOnEncodeAndDecode`. |
| 4 | The server consulted the throttle before looking at the request, so an empty-origins request consumed a token. | Empty requests are answered (with nothing) **before** the throttle is touched — they cost no token and never disturb the tracked-player table. | gametest `anEmptyRequestDoesNotConsumeAThrottleToken` (RED `fixround/red-fabric-gametest.log` → GREEN `fixround/green-fabric-gametest.log`). |
| 5 | At `MAX_TRACKED_PLAYERS` the table was cleared wholesale, dropping unspent bursts of active players. | Prune-to-≤512 **before every insert** (idle first, then least-recently-used) — never a wholesale clear; javadoc states the bound and order. | `AmbientContextServerRulesTest.theCrowdedTableStaysAtItsCapAndNeverClearsLiveBuckets`. |
| 6 | An unknown wire status id caused a decode error (disconnect), and nothing guaranteed it could not land as `OK`. | Unknown ids decode to `REJECTED_NOT_WATER` — always a rejection, never an error, never `OK`. | gametest `unknownStatusIdDecodesToARejectionNotAnError` (both loaders; RED `fixround/red-fabric-gametest.log`). |
| 7 | Coverage gaps the review named: capped-decode paths, and two store boundaries (a rejection overtaken by ordering; an equal-generation response). | Added `oversizedOriginListIsRejectedOnEncodeAndDecode` (5-origin request), `oversizedEntryListIsRejectedOnEncodeAndDecode` (5-entry response), `oversizedCandidateListIsRejectedOnEncodeAndDecode` (97-candidate entry), `equalGenerationResponseIsDroppedByRequestOrdering`, `rejectionAnsweringAnOlderRequestCannotUnseatTheAppliedValue`. | RED: the new tests fail against the old behavior — `fixround/red-common-test.log`, `fixround/red-fabric-gametest.log` → GREEN `fixround/green-common-test-2.log`, `fixround/green-fabric-gametest.log`. |
| 8 | *(found by the fix-round dedicated re-run; long-standing, made visible by item 1)* The sweep discovers in a Chebyshev **square** of radius 24, but eviction kept only a Euclidean **disk** of radius `radius + evictSlack` (26) — the margin between the inscribed circle and the square was discovered, evicted and discovered again, forever. The column set never settled on a motionless world, the geometry signature moved about once per revalidation rotation, and the client re-requested its context indefinitely: dedicated client `dispatches=44 origins=44 responses=44 applied=33 dropped[geom=11]` with every server answer identical (`fp=cccdcaaea6a64311` constant). | Eviction now keeps the same Chebyshev square as the sweep (`WaterDiscovery.within`, radius + slack), so nothing the sweep can find is ever outside the eviction window. | RED reproduced in pure discovery — `fixround/red-fishsim-corner-churn.txt` (`aSaturatedWindowOfStillWaterKeepsOneSignatureTickAfterTick`: signature moves at the first probe tick with `columnsChanged=0` and hundreds of columns added/removed at the margin) and in the world — `fixround/red-dedicated-{server,client}-cornerchurn.log` → GREEN the probe itself, plus `fixround/green-dedicated-{server,client}.log` (5/5 checks; 8 dispatches in 256 ticks, bound 15; the streaming burst settles). |

**Patch keys are handles, not identities** (found while repairing the solo scene mid-fix-round). A
patch key is minted from the 16-block quantum cell where water is first found
(`packKey(qx, qz, instance)`), and when two patches merge the numerically smallest key survives
while the loser's columns are re-keyed wholesale. A patch can therefore change key mid-run: the
solo scene's pond did exactly that in early runs, because a spill skirt crossing a quantum boundary
minted a smaller-keyed pocket whose key folded the pond's away. The fixed scene keeps the pond
inside one quantum (all 49 columns in one cell) and the key held steady for the whole run
(`key=17592202821633`); the dedicated run's key is `-16777208` = `packKey(0, -1, 8)`. Consumers
must treat a key as an opaque handle re-read from discovery each tick, never as water identity.

## Measured bounds (Fabric, this session)

Isolated runs: `run-fabric-solo` (scene in-process, strict replays) and the `run-fabric-server` +
`run-fabric-client` pair (dedicated, port 25599). Raw run directories under
`docs/fish-in-world-phase4/` (gitignored); the extracted capture logs and class-load evidence sit
alongside this doc, and the fix-round runs, extracts and RED reproductions under
`docs/fish-in-world-phase4/fixround/`.

| Measure | Solo scene (frozen code, run 5) | Dedicated pair (client-side counters, fix round) |
|---|---|---|
| Dispatches / origins over the whole scene | 10 / 10 in 299 ticks (bound: 299/20+3 = 17) | 8 / 8 in 256 ticks (bound 15) |
| Responses | 10 from the server + 1 injected stale | 9 (8 from the server + 1 injected); server logged responses `#1..#8` |
| Server work | `responsesSent=10 originsResolved=12 originsRejected=0` | 8 responses, 0 throttled, 0 rejected |
| Equipment-switch latency | rod response ~1 s after the hand-off | rod response ~1 s after the hand-off (`#8` at :22, switch at :21) |
| Rate-limit drops / chunk loads | 0 / 0 | 0 / 0 |

Steady-state cadence both runs: one dispatch per 200 ticks while idle (the periodic re-check) plus
the scene's own events (initial requests, the equipment switch, the world edit, the injected stale
response); every response bounded by 4 entries × ≤ 96 candidates. Server resolution stays at one
resolve per origin per request; nothing resolves per tick. On the joined server, the first seconds
dispatch at the 20-tick minimum while chunks stream in and the discovered set grows (the dedicated
run's `#1..#6` at ~1/s, each superseded answer counted as a geometry drop — `dropped[geom=5]`);
the burst ends when streaming does, after which the cadence above holds.

## Tests

Fix-round counts (2026-10-13; `:common:test` and `:fishsim:test` re-run after the eviction-window
fix): `:common:test` 827 tests / 0 failures / 0 errors / 1 pre-existing skip
(`common/build/test-results/test/*.xml`; run log `fixround/green-common-test-3.log`);
`:fabric:runGametest` **347/347** required tests passed (`fabric/build/junit.xml`, 0
failures/errors); `:neoforge:runGametest` **341/341** ("All 341 required tests passed :)" —
NeoForge writes no JUnit XML; the count comes from its log).

| Suite | Count | Result | Evidence |
|---|---|---|---|
| `:common:test` | 827 | 0 failures, 0 errors, 1 pre-existing skip | `common/build/test-results/test/*.xml` |
| — ambient-specific | 24 | `AmbientContextStoreTest` 13, `AmbientContextServerRulesTest` 5, `AmbientRequestThrottleTest` 6 | same XML |
| `:fishsim:test` | 440 | 404 passed, 0 failures, 36 skipped (re-run in the fix round after the eviction-window fix below; +1 regression probe) | `fishsim/build/test-results/test/*.xml` (extract: `fixround/green-fishsim-test-3.txt`) |
| `:fabric:runGametest` | 347 | all passed, `junit.xml` 347 testcases 0 failures | incl. `AmbientContextGameTests` (9) + 6 packet codec tests |
| `:neoforge:runGametest` | 341 | all passed, `BUILD SUCCESSFUL` | every new test registered in `NeoForgeGameTestRegistration` |
| Solo client scene | 9/9 checks | `ambientcontext.{runtime,received,lane.worms,replay.a,switch.lane,replay.b,geometry.reask,stale.dropped,traffic}` | `fixround/green-solo-console-fixed5.log` (frozen code; run 4 identical on the pre-eviction-fix build) |
| Dedicated client scene | 5/5 checks (+`geometry.reask` skipped: no world edit on a joined server) | same scene, replays skipped (no integrated server); server log is the other side — fingerprints/session equal (`cccdcaaea6a64311 n=16` → `60f4be1d9fdd42ba n=20`, sessions reinterpret equal) | `fixround/green-dedicated-client.log` + `fixround/green-dedicated-server.log` (extract: `green-dedicated-server-responses.txt`) |

Gametest coverage (world-level): authoritative resolve equals the server state; held-rod lane from
the server's own inventory; dimension mismatch rejected; far origin rejected before the unloaded
check; unloaded origin rejected **without loading the chunk**; rate limit drops requests past the
burst; session snapshot takes precedence over a fresh resolve; a different pond near the cast falls
through to a fresh resolve (fix-round: proximity is a near-exact match, not a window); an empty
request does not consume a throttle token; oversized origin/entry/candidate lists throw
`EncoderException`/`DecoderException` past the codec bounds; an unknown wire status id decodes to
`REJECTED_NOT_WATER`, never an error and never `OK`.

## Dedicated-server evidence

- `fixround/green-dedicated-server.log` (readable extract:
  `fixround/green-dedicated-server-responses.txt`) — the fix-round re-run: join, `Done`, and the
  eight authoritative responses with `session/reqGen/fp/n/mode`: `#1` key 1 worms (a first patch
  the client later retained away), `#2..#7` the client's shown patch (key −16777208) worms
  `fp=cccdcaaea6a64311 n=16` at ~1/s while chunks streamed in, then quiet, `#8` after the
  equipment switch `fp=60f4be1d9fdd42ba n=20 mode=rod`.
- `fixround/green-dedicated-client.log` — all 5 checks PASS. The client's `received` capture
  `fp=cccdcaaea6a64311 n=16` and post-switch `fp=60f4be1d9fdd42ba n=20` are **equal to the
  server's logged answers for the same patch** (cross-process agreement, plus the same session
  token — client `e190420e0d093b8a` reinterpreted as a signed long equals the server's
  `-2193180390413747318`).
- The equipment switch is the client's creative-slot hand-off (client writes its local inventory,
  sends the creative packet, resyncs — the exact `CreativeModeInventoryScreen.handleHotbarLoadOrSave`
  sequence; the packet alone is send-only and the server suppresses the echo via `setRemoteSlot`).
  The server's own copy moved (`mode=rod`), the client's context generation moved 5 → 6
  (`ctxDrops=0`), and the injected pre-switch response (`ctxGen=5`, `reqGen=108` newer than
  anything sent) was discarded with `DROPPED_STALE_CONTEXT`, the rod answer surviving — in both
  the solo and the dedicated runs.
- `fixround/green-dedicated-classload.txt` — the server JVM launched with `-Xlog:class+load=info`
  and a grep for `grill24.fishtastic.client` finds exactly **one** hit, the pre-existing
  `TankCosmeticMesh$Snapshot` record (see limitations): **zero Phase 4 client classes**
  (`client.ambient.*`, `client.selftest.*`, `AmbientFishRuntime`, `AmbientContextClient/-Store`,
  `ClientAmbientFish`, `RenderSelfTest`).

The pre-fix capture (`capture-fabric-server.log`, `capture-fabric-client.log`,
`capture-fabric-server-classload.txt`, 30 217 loads / 1 447 distinct `grill24.fishtastic` classes)
records the same agreement on the original four-response scene.

**Not verified cross-process:** candidate *weights* are compared for full equality only in the
solo run (in-process replay of the integrated server's resolve — `candidatesEqual=true` both
phases). On the dedicated pair the comparison is `fingerprint + count + lane` from the server's
log; weights behind an equal fingerprint come from the same server-side resolve either way.

**Loader scope:** the Phase 4 client pipeline is common code; the client scenes were run on Fabric
(solo + dedicated). NeoForge covers the same code through its gametest run and compiles the same
scene code, but was not client-captured for Phase 4.

## Gate table

| Gate clause | Status | Where |
|---|---|---|
| Integrated and dedicated-server clients agree with authoritative species contexts | **Met** | Solo: in-process replay equality (fp, rod lane, `candidatesEqual=true`) both phases; dedicated: client fp/count/session equal to the server's logged answers; gametest `authoritativeResolveMatchesServerState` / `heldRodLaneIsResolvedFromTheServersOwnInventory` |
| Equipment changes cannot be overwritten by stale responses | **Met** | Solo + dedicated phase D: pre-switch entry with a newer request generation but the old context generation → `DROPPED_STALE_CONTEXT`, rod value survives; `equipmentChangeDiscardsInFlightResponseAndStalesTheValue`, `responseToAnOlderRequestNeverOverwritesANewerOne`, `geometryChangeDiscardsAnswersForTheOldWater` |
| Traffic and server resolution work stay bounded | **Met** | Scene dispatch bound (solo 10 ≤ 17, dedicated 8 ≤ 15, incl. the streaming burst), ≤ 4 origins/dispatch, ≤ 96 candidates/entry, 4-entry codec caps, per-player token bucket with 0 throttled requests in any capture, no chunk loading (`unloadedOriginIsRejectedWithoutLoadingTheChunk`), no per-fish/per-frame resolve (policy) |
| Dedicated-server code does not load client classes | **Met for Phase 4** | Class-load log: zero Phase 4 client classes on the server. Caveat: one *pre-existing* client-package record load (`TankCosmeticMesh$Snapshot`, pulled by the common record `FishTankCompositeModelData`) — see limitations |

## Decisions and rejected alternatives

- **Server-authoritative resolve, not a client-side preview with server blessing.** The server
  derives equipment from its own player state; clients never compute the pool they will show.
  Rejected: server sending candidate weights computed client-side (trust and drift problems).
- **Four-level token scheme, not one version int.** Session / geometry / context / request order
  each have a distinct failure mode (reconnect, water change, equipment change, reordering) that
  the others cannot cover; the JUnit suite pins each independently.
- **Derived staleness, not mutation on invalidation.** A generation bump makes every applied value
  stale by comparison; nothing has to hunt down and rewrite stored answers, so a missed
  invalidation event cannot leave a "usable" lie behind.
- **"No response" is a supported state.** A throttled or lost request ages out; the periodic
  re-check is the only retry. Rejected: client retry storms, or treating a missing answer as OK.
- **Rejections clear the value.** A `REJECTED_*` answer means the origin is no longer water —
  keeping the old candidates usable would spawn fish into dry ground. Rejected: treating a
  rejection as a soft failure with the previous value retained.
- **Only shown patches are requested, ≤ 4 origins, one dispatch per 20 ticks minimum.** Rejected:
  a global grid sweep of nearby water (traffic + resolution cost with no consumer yet) and
  per-patch requests as they appear.
- **Bounded candidate list (96, deterministic top-weight in pool order) with a `truncated` flag.**
  Rejected: send-all-candidates (unbounded response) and random truncation (would bias the pool).
- **Token bucket per player** rather than a global clamp: one misbehaving client cannot starve
  others, and the bucket is dropped after idling. Rejected: global request budget.
- **Never load a chunk to answer.** `isLoaded` gates every read; a not-yet-loaded origin is
  rejected outright. Rejected: force-load around the player (the client's discovery window is the
  only place origins can legally come from anyway).
- **Equipment signature through the shared resolver inputs.** The client hashes exactly what the
  server will resolve with (`heldRodPreview` / `standardWormsBaseline` + carried diamonds), so the
  "did my inputs change" question and the server's answer can never disagree about what changed.
- **Client scene performs the vanilla creative hotbar write sequence** (`setItem` → creative-slot
  packet → local menu resync). This mirrors real UI code; discovery: the creative-slot packet alone
  is send-only and the server's handler suppresses the echo, so a harness that only sends moves the
  server's copy while the client's equipment signature (correctly) never changes.

## Limitations and open items

- **Pre-existing server-side class load** (not Phase 4): `grill24.fishtastic.client.compositemodel.
  TankCosmeticMesh$Snapshot` is a data-only nested record used as a component of the common record
  `FishTankCompositeModelData` (which also reads `Snapshot.EMPTY`), so loading that common type on
  a dedicated server pulls the record. No rendering code loads; the fix (moving the record type out
  of the client package) belongs to the concurrent cosmetic-model workstream and was not touched.
- Periodic re-check lag: a time-of-day / weather boundary crosses the fingerprint at most
  `PERIODIC_RECHECK_TICKS` (10 s) after it happens client-side.
- Discovery rotation lag: a block edit inside cached water is visible to the request pipeline only
  when the revalidation cursor reaches that column — at most one full rotation of the known set
  (48 known columns re-read per tick; a fully discovered 24-block window is ≈2 400 columns, so
  ~50 ticks / 2.5 s worst case, usually less). The per-tick signature compare and the 200-tick
  periodic re-check sit on top of that, not underneath it.
- **Patch keys can change on a merge** (keys are handles, not identities — see *Review round and
  fix round*): when two patches join, the numerically smallest key survives and the other's
  columns are re-keyed that tick. Consumers must re-read `patchKeyAt`/`columnsOf` each tick and
  never cache a key as "this body of water".
- Cross-process candidate-weight equality is asserted only via fingerprint+count+lane (see above).
- The equipped rod itself is not part of the client-side request; the server's own view is
  authoritative by design — a desynced client would get the server's lane, which is the intended
  semantics, not a defect.
- Phase 5 consumers must treat every non-`OK` state as "no known pool" (the doc records this
  contract; nothing enforces it yet because nothing consumes contexts).
