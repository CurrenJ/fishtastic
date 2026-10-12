# Fish in World — implementation plan outline

Session continuation checkpoint: [fish-in-world-handoff.md](fish-in-world-handoff.md).
Status: Phase 0 complete (2026-10-12). Phase 1 implemented and regression-tested; final coordinator visual review and latest packaged JAR build remain pending. Work stopped at user usage-limit request. Resume from [Phase 1 handoff](fish-in-world-phase1.md). Baseline and integration findings: [fish-in-world-phase0.md](fish-in-world-phase0.md).

Feature contract: [fish-in-world.md](fish-in-world.md). Follow the phases below in order, with a reviewable result and validation at each gate. Class names below describe responsibilities; finalize names during implementation rather than treating this outline as a prescribed class hierarchy.

## Fixed requirements

- Reuse tank simulation and rendering as much as possible; world fish should look nearly identical to tank fish of the same species and size.
- No rod held: standard-worms pool under local environmental conditions, with neutral personal equipment modifiers. No ownership or consumption of worms is required.
- Supported rod held: actual species context for that rod, including no bait where applicable. Active fishing sessions retain the context that generated their rewards at the hook patch.
- Equipment changes resolve immediately; compatible individuals retain their motion, excluded individuals retire promptly, and weight-only changes alter abundance gradually.
- Species sightings are approximate information. Ambient fish never determine actual catches or grant progression.
- Populations are local client simulations, anchored in world water. No saved entities or per-fish server simulation.

## Architecture outline

| Responsibility | Ownership and reuse |
|---|---|
| Species eligibility and effective weights | Shared Minecraft-aware fishing resolver, used by real fishing, Almanac, and ambient snapshots. Server authoritative for ambient context. |
| Nearby water discovery and geometry | Client adapter producing bounded, world-anchored domains; reuse `FlockDomain`/`VoxelDomain` and world-query infrastructure where suitable. |
| Fish motion | Existing `FlockEngine` and tank locomotion parameters, initially following the multi-tank path. `fishsim` remains Minecraft-free. |
| Species descriptors and appearance | Shared tank mapping, size calibration, `FishAnimator`, and render-state helpers extracted only where needed. |
| Population lifetime and reconciliation | Ambient client manager with stable fish IDs, patch state, independent random streams, and bounded caches. |
| Rendering and loader lifecycle | Common world renderer and client manager, wired into both loaders' existing world-submit, tick, and cleanup hooks. |

Keep the server's fishing-context patches separate from the client's simulation volumes: connected water can span several fishing contexts, and a bounded simulation volume is not a complete body of water. Domain changes must not implicitly create fresh species rolls.

## Phase 0 — record the baseline and settle integration seams

**Completed:** recorded the shared descriptor/group-pose seams, carry/frame limitations, both loader hooks, equipment/session gaps, bounded prototype proposals and headless swim/glide/drift/benthic records. Baseline: fishsim 347 passed / 36 skipped; common 766 passed / 1 skipped; no failures. Companion squid work is concurrent, so preserve its files and inspect fresh diffs before shared changes. Client sprite/lighting/fog parity remains a Phase 1 gate; headless fixtures do not establish it. See the [Phase 0 record](fish-in-world-phase0.md) for commands and evidence.

1. Map the tank/group path: descriptor construction, size and locomotion gates, engine rebuild/carry, animation transforms, and deferred render submission.
2. Map real fishing equipment selection and environment resolution, including active-session snapshots and cast-triggered effects. Resolve any hand/active-rod discrepancy through the same shared rule used by casting.
3. Record representative tank motion/render fixtures for swimming, gliding, drifting, and bottom-associated fish. Identify existing golden/parity tests to preserve.
4. Choose prototype bounds for horizontal/vertical discovery, scanned cells per tick, fish count, domains, field samples, and pending context requests. Start with the spec's proposed radius/count and measure rather than treating them as proven defaults.

**Gate:** a short implementation map and reproducible baseline. Existing tank behavior and unrelated in-progress work remain intact.

## Phase 1 — shared tank visuals and a fixed-pool world prototype

**Implementation present; gate pending review:** shared visuals/pose helpers, disabled-by-default dev fixture and both loader hooks exist. Common 778 passed / 1 skipped; fishsim 347 passed / 36 skipped. Both loaders completed previous 14-PASS capture scenes; final Fabric inward-camera refresh passed, final NeoForge refresh was running when work stopped. Latest capture-only snapshot additions compiled on both loaders but require an incremental packaged build. See the [Phase 1 handoff](fish-in-world-phase1.md) for exact remaining steps and evidence.

1. Extract the minimum shared descriptor and render helpers from `TankFlockAdapter`, `TankGroupFlock`, and `FishTankBlockEntityRenderer`; continue using `FishAnimator` directly.
2. Build a thin ambient runtime around the existing multi-tank engine path. Give every ambient individual a stable identity and its own deferred render state.
3. Render a fixed, known set of species in a small static test-water domain. Step once per client tick; render only interpolates and submits.
4. Connect both loaders using their current world-submit events. Apply world transforms, per-fish lighting, fog, bounds-based culling, and the same species scale/poses as tanks.

**Gate:** side-by-side tank/world appearance and motion agree under comparable conditions on Fabric and NeoForge. Shared refactoring passes existing tank regressions. Do not create a separate ambient animation model to solve a domain problem.

## Phase 2 — bounded natural-water domains

1. Discover usable loaded water incrementally around the player. Identify connected regions and stable world patch keys without scanning an entire ocean.
2. Adapt occupancy, fluid surface heights, collision geometry, and floors to the shared domain interface. Evaluate `VoxelDomain` first; add a natural-water domain implementation only where its block-union assumptions are insufficient.
3. Validate full animated body clearance, not just fish-center occupancy. Account for size, yaw, bobbing, floor poses, partial fluids, and waterlogged obstacles. Omit an individual that cannot fit; do not inherit the tank's hover fallback as ambient behavior.
4. Build/rebuild terrain fields within explicit budgets. Invalidate on nearby block/fluid changes and chunk unloading. Cache local geometry with bounded lifetime and memory.
5. Preserve state as the local window moves or a domain changes. Handle discovery boundaries through an outer simulation margin and gradual retirement, so fish do not visibly turn against a player-centered invisible wall. Choose and verify the exact boundary strategy here.

**Gate:** stable fixed-pool fish in river, ocean, shallow pond, irregular floor, and disconnected-pool fixtures. No dry-land crossing, surface clipping, recenter resets, or field-build spikes beyond the agreed budget. Domain rebuilds preserve valid individuals and safely retire invalid ones.

## Phase 3 — one side-effect-free fishing pool resolver

1. Extract candidate filtering and effective species weights from the existing fishing/Almanac paths into a shared result: environment context plus species IDs and final raw weights.
2. Preserve existing filter and weighting order, including zone gates, bait-exclusive pools, vanilla exclusion with bait, environment/charm multipliers, and existing bait flattening. Preserve candidate ordering and reward RNG usage in the real fishing path.
3. Support explicit input modes: standard-worms baseline, held-rod preview, and active-session snapshot. Read the registered standard-worms definition rather than maintaining a decorative species list.
4. Separate species-affecting inputs from quality/difficulty/treasure-only inputs. Store a species-context version/fingerprint suitable for invalidation and reuse.
5. Audit hook-dependent/custom item weights and tool input: a preview has no live hook. Use shared explicit inputs wherever possible and define unsupported preview behavior rather than invoking a fake cast with side effects.
6. Capture the active session's resolved species context when its targets are generated, allowing the ambient hook patch to retain that snapshot without rerolling or reconstructing it from later equipment.

**Gate:** resolver output matches pre-refactor fishing and Almanac behavior across representative contexts. Baseline matches standard-worms fishing; previews consume nothing, trigger no storms, and do not advance catch RNG. Existing catches retain their behavior.

## Phase 4 — authoritative context snapshots

1. Add bounded patch-context requests and species-weight responses through the common packet abstraction and both loader registrars. The client requests water origins; the server derives equipment and environment from its own state.
2. Validate dimension, nearby loaded water origin, request count/rate, and response size. Never load chunks to satisfy ambient requests. Resolve world/player state on the main thread.
3. Version responses by world session, patch, geometry/context generation, and request generation. Discard late responses after rod switches, terrain changes, reloads, or dimension changes.
4. Refresh on meaningful environmental/equipment changes, with a bounded periodic check for time/weather/moon changes. Avoid per-fish or per-frame pool resolution.
5. Define pending/invalid snapshot behavior: do not spawn from an unknown pool; retain individuals only under the bounded transition policy. The resolved hook-session snapshot takes precedence for its patch until the session ends.

**Gate:** integrated and dedicated-server clients agree with authoritative species contexts. Equipment changes cannot be overwritten by stale responses. Traffic and server resolution work stay bounded; dedicated-server code does not load client classes.

## Phase 5 — weighted population and equipment transitions

1. Sample ambient individuals with a separate RNG using the spec's tunable 75% raw / 25% square-root-flattened mixture. Apply this after fishing weights are resolved. Never modify catch weights.
2. Restrict the visual candidate set to species/sizes the domain can represent, then normalize within that set. Avoid infinite placement retries or accidental bias from repeatedly failing large-species spawns; explicitly record omissions for debugging.
3. Derive density from usable water and the client budget. Spawn individuals rather than expanding each sampled species into an unchecked school; use existing compatible flocking motion.
4. Reconcile each context update by stable ID: retain eligible fish and their motion; retire excluded fish; fill vacancies gradually from the new pool. For weight-only changes, let ordinary turnover shift abundance. Keep lifetimes comparable across species.
5. Implement short retirement fades through a shared compatible render mechanism, preserving normal tank submission. Start with the proposed two-second eligibility transition and 15–30-second weight convergence, then measure.
6. Retain bounded patch/equipment random state across brief switching and re-entry. Reuse compatible live individuals across contexts; do not let cached snapshots resurrect now-ineligible fish or cause an immediate fresh rare-species roll. Natural turnover continues with simulation time.

**Gate:** rod/no-rod and bait/charm switches retain common individuals without resets or morphing. Excluded fish retire within the target bound; observed long-run occupancy follows configured visual weights. Quality-only changes do not disturb the population.

## Phase 6 — settings, cleanup, and performance

1. Add ambient enable/density controls through the existing client settings system. Density bounds simulation, discovery, rendering, and context demand together.
2. Complete cleanup on disconnect, teleport, dimension change, datapack/resource reload, and unloading. Turning the feature off stops its work and releases runtime state.
3. Expose development counters for fish count, domains/cached cells, field rebuilds, omitted species, context resolves/requests, packet bytes, simulation time, and render cost.
4. Profile ambient fish alongside tanks, companions, and vanilla fish in representative scenes. Tune budgets/defaults against an agreed reference machine; confirm discovery and rebuild spikes as well as steady-state cost.

**Gate:** measured bounded cost and clean lifecycle on both loaders. Disabling the feature leaves fishing outcomes and progression unchanged.

## Phase 7 — acceptance and release readiness

Run the spec's acceptance matrix, focusing on equipment changes, boundary contexts, terrain updates, appearance parity, and multiplayer. Compare raw versus flattened populations for rare-sighting expectations and Almanac usefulness. Record final tuning values and any unsupported visual species/domain cases in the spec before sign-off.

Use targeted plain-Java tests for sampling, lifetime bias, identity carry, and domain invariants; use common unit tests where no running world is required. Use shared GameTests for authoritative world/equipment/context behavior, and real client captures for rendering/fog/visual parity. Headless motion fixtures cannot prove Minecraft rendering correctness.

Validation commands, to run as applicable during implementation:

```
.\gradlew.bat :fishsim:test :common:test
.\gradlew.bat :fabric:runGametest
.\gradlew.bat :neoforge:runGametest
.\gradlew.bat build
```

Verify that the intended GameTests actually ran; build success alone is insufficient. Generate test structures only if new fixtures need them. Client acceptance runs use `:fabric:runClient` and `:neoforge:runClient`, plus a dedicated-server session for the network/lifecycle checks.

## First implementation milestone

Complete Phases 0–1: the existing tank engine draws a fixed set of fish in a test-water volume on both loaders, with shared appearance/motion and no tank regressions. This gives us a concrete result to assess before introducing discovery, networking, or live pool selection.

## Reference locations

- `common/.../client/renderer/{TankFlockAdapter,TankGroupFlock,FishTankBlockEntityRenderer,FishAnimator}.java`
- `fishsim/.../core/{FlockEngine,FishSpec,Locomotion,Tunables}.java` and `fishsim/.../domain/{FlockDomain,VoxelDomain,FloorField}.java`
- `common/.../server/FishingMinigameManager.java` and `common/.../item/FishtasticFishItem.java`
- `common/.../component/BaitEffect.java` and `common/.../network/{FishtasticPackets,IPacketRegistrar}.java`
- `common/.../client/companion/{ClientCompanions,CompanionWorldAdapter}.java` and `fishsim/.../companion/LocalVoxels.java`
- `fabric/.../FishtasticFabricClient.java` and `neoforge/.../FishtasticNeoForgeClient.java`

Read the canonical modding-guide topics before changing the corresponding systems. In particular, use `08-networking.md` and `18-game-tests.md` for packets and integration tests. This outline adds no new entities, items, registries, or progression systems.
