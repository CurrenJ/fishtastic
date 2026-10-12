# Fish in World — Phase 0 baseline and integration map

Recorded 2026-10-12 on MC 26.1.2, Java 25, checkout HEAD `8c99327cb051875dbf84d761f98ea4abc38ff252`. Phase 0 changes documentation and diagnostic fixtures only. No ambient runtime exists yet, and no client visual parity is claimed.

Read canonical guide topics `01`, `03`, `04`, `07`, `08`, and `18` before investigation. The referenced `09-rendering.md` is absent from `D:/GitHub/modding-guide`; used `_research/fishtastic-findings.md` §17 and current repository rendering code as the fallback. The current renderer is the source for the concrete seams below.

## Reproducible baseline

From the repository root:

```powershell
.\gradlew.bat :fishsim:test :common:test --rerun-tasks --console=plain
```

Completed successfully in 1m 42s, with all 11 tasks executed. Counts were read from the resulting JUnit XML rather than inferred from build success:

| Suite | Suites | Tests | Passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|---:|
| `fishsim` | 70 | 383 | 347 | 36 | 0 / 0 |
| `common` | 32 | 767 | 766 | 1 | 0 / 0 |

Full console output: [baseline-test.log](fish-in-world-phase0-baseline-test.log). Gradle initially failed before executing tests because the sandbox blocked its existing wrapper-cache lock; the authorized retry using that cache succeeded. Existing deprecated/unchecked/Unsafe warnings remain. No failing test was observed, and no unrelated code was repaired.

Preserve especially `ParityTest`, `GoldenTrajectoryTest`, `VoxelGoldenTrajectoryTest`, `RebuildCarryTest`, `PromotionCarryTest`, `LocomotionTest`, `GlideTest`, `DriftTest`, `BenthicTest`, and the common `GroupOwnersTest`, `FishAnimator*`, `TankFloorsTest`, `TankObstaclesTest`, `TankSheltersTest`, `TankReactionsTest`, and `TankGroupsBudgetTest`.

The workspace already contained companion documents, logs, untracked assets, and `BenthicShapeProbe.java`. Another agent is working on companion squid movement concurrently. These counts describe this run of the shared working tree, not a pristine-HEAD or immutable companion baseline. Recheck source diffs immediately before subsequent shared extraction; do not overwrite drift, squid, companion, or locomotion changes.

## Headless motion records

[FixtureCapture.java](fish-in-world-phase0/FixtureCapture.java) uses the existing `FlockEngine`, `Tunables.GROUP`, `VoxelDomain`, and Java2D `FrameRenderer`. It adds no Gradle task or test. Reproduce with a Java 25 executable after `:fishsim:classes` (quote the `-D` argument in PowerShell):

```powershell
$phase0Java = 'C:/Users/curre/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
& $phase0Java '-Djava.awt.headless=true' --class-path fishsim/build/classes/java/main docs/fish-in-world-phase0/FixtureCapture.java docs/fish-in-world-phase0/fixtures
```

Each mode runs three synthetic descriptors with lengths 0.26 / 0.30 / 0.22 blocks, species IDs 1 / 1 / 2, seed 42, base yaw 0°, rotation jitter 20°, no watcher/shelters/obstacles, and a full 4×2×3 voxel domain. Runs last 1,200 fixed steps (60 seconds); CSV records every 40 ticks, including initial state. Contact sheets show ticks 0 / 240 / 480 / 720 / 960 / 1,200, read left to right then top to bottom.

| Mode | Contact sheet | Top view | Motion state |
|---|---|---|---|
| Swimming | [strip](fish-in-world-phase0/fixtures/free_swim-strip.png) | [top](fish-in-world-phase0/fixtures/free_swim-top.png) | [CSV](fish-in-world-phase0/fixtures/free_swim-trace.csv) |
| Gliding | [strip](fish-in-world-phase0/fixtures/glide-strip.png) | [top](fish-in-world-phase0/fixtures/glide-top.png) | [CSV](fish-in-world-phase0/fixtures/glide-trace.csv) |
| Drifting | [strip](fish-in-world-phase0/fixtures/drift-strip.png) | [top](fish-in-world-phase0/fixtures/drift-top.png) | [CSV](fish-in-world-phase0/fixtures/drift-trace.csv) |
| Benthic | [strip](fish-in-world-phase0/fixtures/benthic-strip.png) | [top](fish-in-world-phase0/fixtures/benthic-top.png) | [CSV](fish-in-world-phase0/fixtures/benthic-trace.csv) |

Exporter output: [fixture-capture.log](fish-in-world-phase0/fixture-capture.log). All four casts retained their declared effective mode and reported zero backstop engagements. These are capsule motion records, not Minecraft species renders: they cannot verify sprite scale, animation transforms, water transparency, fog, world light, or shore/underwater appearance. They are diagnostic snapshots, not new golden assertions.

## Tank descriptor and simulation seams

| Existing seam | What ambient should share / preserve |
|---|---|
| `FishTankBlockEntityRenderer.resolveFishRender` + `ResolvedFishRender` | Item-keyed datapack profile lookup, animation fallback, render calibration. Currently package-private and nested in the BER; extract a small client helper rather than depend on a tank renderer instance. |
| `TankFlockAdapter.renderedLength` | Recorded size / 100 × profile calibration; unmeasured stacks use 0.5 blocks. Ambient must choose explicit plausible sizes, otherwise every species receives this large fallback. |
| `TankFlockAdapter.locomotionOf` | HorizontalSwim→FREE_SWIM, BellyDown→GLIDE, UprightFloat→DRIFT, FloorSit/UprightSit→BENTHIC, Planted→ANCHORED. Share this mapping; do not tune a parallel ambient motion model. |
| `TankFlockAdapter.speciesId` / `shelterUseOf` | Registry item ID for species-aware schooling; shelter usage from the profile's swarm definition. Domain-specific shelter geometry remains adapter-owned. |
| `TankGroupFlock.rebuild` | Multi-tank reference: `Tunables.GROUP`, planar `FlockEngine.rebuildPreserving`, yaw 0, world anchor + local offsets, one copied stack/config and deferred `ItemStackRenderState` per fish. Ambient identity comes from stable individual IDs, not tank position + inventory slot. |
| `TankGroupFlock.GroupSplit` / `TankFishBudget` | Tank quota and ownership mechanics are tank-specific. Reuse descriptor/engine behavior, not inventory-driven school counts or a failed-size hover fallback. Ambient samples individual slots and omits unsupported/unfit candidates. |
| `ClientTankFlocks.tickAll` / group `stepIfDue` | Fixed 20 Hz step once per client tick; world tick-rate freeze respected. Rendering interpolates without advancing time. Tanks themselves are warmed by render extraction and evicted after inactivity; ambient lifecycle must instead retain world-patch state independent of camera visibility. |

Single tanks retain a bitwise-locked legacy binary path under `Tunables.DEFAULT`. Obstacles/shelters can promote them to the planar group path. Extract from the group submission first and keep both existing tank paths equivalent; do not replace the legacy path just to simplify ambient code.

`FlockEngine.rebuildPreserving` accepts new-index→old-index carry maps (`-1` for a new fish). It carries current/previous position and yaw, velocity, heading, speed, bank smoothing, tail phase, seeds, home depth, shape drive, wander/spin/noise state, burst phase/drive, anchored state and shelter/startle state. Gates are reevaluated, and invalid crawler floor positions can be replaced. Simulation time survives rebuilds. `setTunables` updates tunings but does not rerun gates or scatter. This preserves compatible identities only when the adapter supplies the right map and frame.

**Frame changes are a separate risk:** carry positions are local coordinates and are not rebased against a changed world anchor. Use one stable world origin for the static prototype. Moving-window/domain recentering later needs an explicit world-preserving rebase and validation. Rebuild carry alone does not prove terrain containment for all modes after arbitrary natural-water changes.

## Appearance and world submission seams

`FishTankBlockEntityRenderer.submitGroupSwimmers` is the reference per-fish transform: translate by anchor/engine position plus `FishAnimator.floorPoseLift`; reseed the shared synchronous `Random` with `engine.seeds[i]`; dispatch the existing swimming/gliding/drifting/benthic/anchored animator with its established phase, shape, bank, and `yaw + 180°` convention; apply the calibrated uniform scale; resolve `ItemDisplayContext.FIXED`; submit that individual's render state. A shared group-pose helper should keep this math in one place. Each deferred fish needs its own `ItemStackRenderState`; sharing one produces last-fish overwrite.

The tank's lighting and ordering cannot be copied wholesale. `extractRenderState` applies `TankInteriorLight` and group fish receive the drawing tank's light. Ambient should sample ordinary world light at each fish. `FlockEngine.interpolate` orders by world Z, suitable for the tank's established view convention; arbitrary world camera angles need camera-aware ordering/culling without mutating sim time or changing tank ordering. World fish should use ordinary fog/depth and omit quality outlines because sightings convey no reward quality. Resource/model reload must invalidate their render states.

| Loader | Existing hook to extend |
|---|---|
| Fabric | `FishtasticFabricClient`: `ClientTickEvents.END_CLIENT_TICK`, `LevelRenderEvents.COLLECT_SUBMITS` (currently `ClientCompanions.submitWorld`), connection JOIN/DISCONNECT and client TAGS_LOADED. |
| NeoForge | `FishtasticNeoForgeClient`: client post-tick handler and `SubmitCustomGeometryEvent` via `onSubmitCustomGeometry`, login/logout handlers, `TagsUpdatedEvent`, client reload listener registration. |

Use a separate common ambient manager/renderer; loader event subscriptions are thin additions next to these hooks. Companion world submissions already receive a camera-relative pose stack; use that coordinate contract. Track level identity for dimension changes and missing-world cleanup, independent of connect/disconnect events. Keep all ambient classes client-only and out of common server packet registration.

`RenderSelfTest` already runs through both client entrypoints, takes screenshots and logs checks when `<run dir>/fishtastic_render_selftest` exists. It creates/uses its named self-test world and the marker persists across launches. Extend it with an opt-in ambient parity scene in an isolated disposable run directory; never reuse a user's play world. Phase 0 did not arm it or launch a desktop client. Actual Fabric/NeoForge captures remain a Phase 1 gate.

## Equipment and fishing-context seams

`FishingMinigameManager.generateTargetsForOrigin` builds loot parameters, uses the player's catch RNG, resolves hook-origin biome/time/weather/moon/zones, filters candidates, and rolls rewards/quality/targets. **Do not call it for ambient previews.** Extract its side-effect-free pool/context slice in Phase 3 alongside `computeTopWeightedFish`.

Preserve filter/weight order: `ItemTags.FISHES` iteration → bait-exclusive tag → vanilla allowed only for null/NO_BAIT → zone gate → `FishtasticFishItem.getFishingLootWeight`. The weight function includes custom `getAdditionalWeight(LootParams)`, authored environment multipliers, existing pool/group bait flattening, bait/charm group multipliers and night bonus; Fishtastic weights clamp to at least one. Zero environment multipliers are not eligibility gates. Hooks' currently used quality/treasure/trash fields do not affect this species slice.

Default mode should resolve the registered `FishtasticItems.WORMS` bait definition with neutral equipment and the water patch's surface origin. No inventory lookup, ownership requirement, consumption, catch RNG, or cast-side effects. Held-rod mode reads that rod's actual component contents. Storm Charm mutates weather/consumes itself in `startSession` before target resolution; holding it must not execute this path.

There is a real hand seam: `FishtasticFishingRodItem.use(level, player, hand)` delegates to vanilla casting with the explicit hand, but manager `findFishtasticRod` and the hook's rod lookup choose main-hand tagged rod first, then offhand. A bobber does not currently record exact casting hand/rod identity. `LootParams.TOOL` currently comes from `player.getUseItem()` rather than the selected rod. Resolve this explicitly when extracting the shared equipment rule; changes to casting/equipment precedence need their own tests and must not be silently introduced by a client preview.

`ActiveSession` stores generated rewards, start time, biome/time/weather/zones, bait-save decision and bait item ID. It lacks the resolved candidate weights, moon phase, precise origin, and casting rod identity. Capturing a true active-session pool needs new server state at the same resolution that generates targets; reconstructing from later held equipment is insufficient. Tutorial/fixed sessions also need an explicit policy, because their forced target need not represent a normal fishing pool. Phase 1 does not depend on this work.

For custom item weights, inventory all overrides before Phase 3. Normal fishing supplies a live hook and TOOL/luck; preview cannot assume arbitrary hooks are irrelevant or fake a cast. Packet requests/responses later belong in `FishtasticPackets` through `IPacketRegistrar`/`IPacketContext`, with loaded-origin validation, caps, main-thread resolution and generation checks.

## Bounded prototype proposals

These are initial hard caps for implementation/testing, not measured release defaults. The first phase uses only a static domain; discovery and networking values reserve bounds for later phases.

| Resource | Proposed initial value | Reason / gate |
|---|---|---|
| Phase 1 static domain | 4×2×3 blocks, one stable origin, up to 8 fish | Small enough for reproducible shared-group comparisons; 1,989 distance samples at current resolution. |
| Natural-water search | 24-block horizontal radius, ±8 blocks vertical | Keeps the design radius while explicitly excluding unbounded deep-ocean scans. |
| Discovery | ≤512 new cell classifications/tick; ≤1,024 combined block/fluid reads/tick; collision queries separately counted within the same budget | Incremental loaded-only search. Full radius volume is about 40,817 cells; do not synchronously materialize it. |
| Cached geometry | ≤32,768 classified cells total; bounded pending queue and explicit eviction | Search window can exceed cache; streaming must stop/evict, not silently overflow. |
| Individuals/domains | ≤32 individuals total, ≤4 live domains | Global caps include retained/fading fish; no per-species school multiplication. |
| Per-domain field box | Start ≤8×4×8; ≤18,513 field samples/domain; ≤80,000 total | Existing field stores four floats/sample (~1.2 MiB for 80,000, before floor/grid/objects). Sparse occupancy still costs its entire bounding box. |
| Field rebuild | ≤1 capped rebuild/tick initially, count and time every rebuild | Existing `DistanceField` builds synchronously; a cap is not a proven latency bound. If measured spikes are unacceptable, Phase 2 must add incremental preparation or smaller domains before natural discovery ships. |
| Context demand (Phase 4) | ≤2 pending requests; ≤4 patch resolutions/sec/player; ≤4 active patch snapshots | Initial bounded traffic proposal. Must support equipment invalidation and stale-response rejection; profile server resolution work. |

`VoxelDomain` represents full unit-cube unions with inset, forced closed faces, a floor field and obstacle boxes. It does not itself represent fractional fluid heights or full arbitrary waterlogged collision geometry. `LocalVoxels` offers lazy 32³ block caching plus overflow (65,536 entries), recenters after six blocks, and reduces solid/water/top information; its companion adapter does not check loaded chunks before each query. Reuse query/counter patterns carefully, not its current budgets or geometry precision. Ambient needs explicit loaded-only checks and conservative full animated body fit.

Remaining design choices have concrete gates: compare shore/underwater sprite visibility on both loaders; choose a world-boundary strategy without invisible player-centered walls; measure field rebuild latency before expanding domains; settle explicit ambient size policy (profile mean is a sensible first visual value); define tutorial/custom-weight preview policies. None requires changing the shared locomotion algorithms in Phase 1.

## Recommended Phase 1 slice

Extract only shared profile/descriptor resolution and the established planar group-pose math, with tank regressions retained. Add an opt-in client ambient development fixture with stable IDs, one fixed world-anchored 4×2×3 water domain, explicit sizes and deterministic known species: bluegill (swim), giant manta ray (glide), Portuguese man o' war (drift), and starfish/plaice (benthic). Retain any concurrently updated drift behavior; do not edit companion simulation. Use one state per fish, world light, camera-aware culling and both loader submit/tick hooks. Add an isolated opt-in self-test scene for comparable tank/world captures. Keep fishing, natural discovery, networking, transitions and release defaults out of this slice.

Phase 0's map/baseline gate is complete. Phase 1 starts only after coordinator review. The client visual gate remains unverified until actual in-game evidence exists.
